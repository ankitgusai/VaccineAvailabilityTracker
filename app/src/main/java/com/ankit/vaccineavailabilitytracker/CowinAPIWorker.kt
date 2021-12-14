package com.ankit.vaccineavailabilitytracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkerParameters
import androidx.work.rxjava3.RxWorker
import io.reactivex.rxjava3.core.Single
import java.lang.Exception

class CowinAPIWorker(appContext: Context, workerParams: WorkerParameters) : RxWorker(
    appContext,
    workerParams
) {
    override fun createWork(): Single<Result> {
        return Single.create { emitter ->
            try {
                retrieveVaccinationLocationData(applicationContext)
                showNotificationIfSlotsAvailable()
                emitter.onSuccess(Result.success())

            } catch (e: Exception) {
                e.printStackTrace()
                emitter.onSuccess(Result.failure())
            }
        }
    }

    fun showNotificationIfSlotsAvailable() {
        val spManager = SpManager(applicationContext)

        if (!spManager.is18PlusSlotAvailable) {
            return
        }

        createChannel()

        val builder = NotificationCompat.Builder(applicationContext, "HeadsUpNotifications")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentTitle("Vaccination slots are available!")
            .setContentText("at pin ${spManager.pinCode} ")
            .setPriority(NotificationCompat.PRIORITY_HIGH)


        val notifyIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val notifyPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        builder.setContentIntent(notifyPendingIntent)


    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = applicationContext.getString(R.string.channel_name)
            val descriptionText = applicationContext.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val mChannel = NotificationChannel("HeadsUpNotifications", name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager =
                applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }
}