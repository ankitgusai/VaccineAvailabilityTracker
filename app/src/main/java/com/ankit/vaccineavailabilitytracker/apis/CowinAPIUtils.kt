package com.ankit.vaccineavailabilitytracker.apis

import android.content.Context
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.ankit.vaccineavailabilitytracker.R
import com.ankit.vaccineavailabilitytracker.SpManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dirty but simple way to build API calls with retrofit.
 */
private val cowinAPI by lazy {
    Retrofit.Builder()
        .baseUrl("https://cdn-api.co-vin.in/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CowinAPI::class.java)
}


/**
 * Synchronous API call to COWIN api.
 *
 * this logic is separated because it can be directly called from UI on demand or can be called
 * by WorkManager scheduled job.
 */
fun retrieveVaccinationLocationData(applicationContext: Context) {
    val date = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
    val spManager = SpManager(applicationContext)

    //Api call
    cowinAPI.getVaccineSpotsByPin(spManager.pinCode, date).execute().body().also { pojo ->

        //parsing data and building user-friendly string message with spannable
        //s
        spManager.totalQueries = spManager.totalQueries + 1

        spManager.totalAvailableLocations = pojo?.centers?.size ?: 0

        val builder = SpannableStringBuilder()
        var is18PlusSlotAvailableOverall = false
        builder.append("Slot(s) for 18+ available at:\n\n")

        pojo?.centers?.forEach { center ->

            var is18PlusEligibleForSession = false
            //var is18PlusSlotAvailableForSession = false
            var totalOpenSLots = 0

            center.sessions?.forEach { session ->

                if (session.minAgeLimit == 18) {
                    is18PlusEligibleForSession = true
                    //is18PlusSlotAvailableForSession = session.availableCapacity > 0
                    totalOpenSLots += session.availableCapacity
                }
            }

            if (is18PlusEligibleForSession) {
                is18PlusSlotAvailableOverall = true


                val color = if (totalOpenSLots > 0) {
                    ContextCompat.getColor(applicationContext, R.color.green)
                } else {
                    ContextCompat.getColor(applicationContext, R.color.yellow)
                }

                val statusFlag =
                    if (totalOpenSLots > 0) "$totalOpenSLots available" else "all booked"

                val centerString =
                    SpannableString("   ${center.name}(${center.blockName}) (${statusFlag})\n")

                centerString.setSpan(
                    ForegroundColorSpan(
                        color
                    ),
                    0,
                    centerString.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                builder.append(centerString)
            }

        }

        spManager.is18PlusSlotAvailable = is18PlusSlotAvailableOverall
        spManager.lastChecked = System.currentTimeMillis()

        //gotta go HTML way to preserve the span added(short of hack but works)
        spManager.consolidatedString = if (is18PlusSlotAvailableOverall) {
            Html.toHtml(builder, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE)

        } else {
            "No slots available at given pin code :/"
        }


    }
}