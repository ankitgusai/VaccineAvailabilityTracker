package com.ankit.vaccineavailabilitytracker

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.ankit.vaccineavailabilitytracker.databinding.ActivityMainBinding
import com.trello.rxlifecycle4.android.lifecycle.kotlin.bindToLifecycle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mBinding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)

        val viewModel by viewModels<MainActivityViewModel>()

        mBinding.data = viewModel

        mBinding.editTextTextPersonName.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                viewModel.inputSendCallFromKeyboard.onNext("")
                true
            }
            false
        }

        viewModel.outputGoToCowinLoginPage
            .bindToLifecycle(this)
            .subscribe {
                startActivity(it)
            }
        viewModel.outputShowInvalidPin
            .bindToLifecycle(this)
            .subscribe {
                Toast.makeText(applicationContext, "Please enter valid pin!", Toast.LENGTH_LONG).show()
            }
        viewModel.outputServerNotResponding
            .bindToLifecycle(this)
            .subscribe {
                Toast.makeText(applicationContext, "Cowin server not responding!", Toast.LENGTH_LONG).show()
            }
    }
}

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val spManager = SpManager(application)
    private val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm aa", Locale.US).format(Date())

    val isTrackingActive = ObservableBoolean()

    val pinCode = ObservableField<String>()
    val startedOn = ObservableField<String>()
    val lastChecked = ObservableField<String>()
    val totalQueries = ObservableField<String>()
    val consolidatedMessageFromLastCheck = ObservableField<Spanned>()
    val isProcessing = ObservableBoolean()

    // val inputTrackingStatus = PublishSubject.create<Boolean>()

    val inputCheckNowClicked: PublishSubject<String> = PublishSubject.create<String>()

    val inputSendCallFromKeyboard: PublishSubject<String> = PublishSubject.create<String>()

    val outputShowInvalidPin: PublishSubject<String> = PublishSubject.create<String>()

    val outputServerNotResponding: PublishSubject<String> = PublishSubject.create<String>()

    val inputGoToCowinLoginPage: PublishSubject<String> = PublishSubject.create<String>()

    val outputGoToCowinLoginPage: Observable<Intent> = inputGoToCowinLoginPage
        .map { Intent(Intent.ACTION_VIEW, Uri.parse("https://selfregistration.cowin.gov.in")) }
        .filter { it.resolveActivity(application.packageManager) != null }

    val inputStopTracking: PublishSubject<String> = PublishSubject.create<String>()


    private val outputStartTracking = inputSendCallFromKeyboard
        .filter {
            if (TextUtils.isEmpty(pinCode.get())) {
                outputShowInvalidPin.onNext("Please enter valid pin code")
                return@filter false
            }

            if (pinCode.get()!!.trim().length != 6) {
                outputShowInvalidPin.onNext("Pin code needs to be 6 character long")
                return@filter false
            }

            return@filter true
        }
        .doOnNext {
            spManager.pinCode = pinCode.get()!!
            spManager.startTimeStamp = System.currentTimeMillis()
            isTrackingActive.set(true)


            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val fetchRequest =
                PeriodicWorkRequest.Builder(CowinAPIWorker::class.java, 1, TimeUnit.HOURS)
                    .addTag("VaccineLocationFetchRequest")
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(application).enqueue(fetchRequest)

        }

    private val currentStatusCheck: Observable<String> =
        PublishSubject.merge(outputStartTracking, inputCheckNowClicked)
            .doOnNext {
                isProcessing.set(true)
            }
            .observeOn(Schedulers.io())
            .filter {
                try {
                    retrieveVaccinationLocationData(application)
                    return@filter true
                } catch (e: Exception) {
                    e.printStackTrace()
                    outputServerNotResponding.onNext("Cowin server not responding...")
                    return@filter false
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                isProcessing.set(false)
            }


    init {
        isTrackingActive.set(!TextUtils.isEmpty(spManager.pinCode))
        setData()

        currentStatusCheck.subscribe {
            setData()
        }
        inputStopTracking.subscribe {
            spManager.clearAll()
            isTrackingActive.set(false)

            WorkManager.getInstance(application)
                .cancelAllWorkByTag("VaccineLocationFetchRequest")

        }
    }

    private fun setData() {
        if (isTrackingActive.get()) {
            pinCode.set(spManager.pinCode)
            startedOn.set(formatter.format(spManager.startTimeStamp))
            lastChecked.set(formatter.format(spManager.lastChecked))
            consolidatedMessageFromLastCheck.set(
                Html.fromHtml(
                    spManager.consolidatedString,
                    Html.FROM_HTML_MODE_LEGACY
                )
            )
            totalQueries.set(spManager.totalQueries.toString())
        }
    }
}

interface CowinAPI {
    @GET("api/v2/appointment/sessions/public/calendarByPin")
    fun getVaccineSpotsByPin(
        @Query("pincode") pin: String,
        @Query("date") date: String
    ): Call<VaccineLocationByPinPOJO>
}