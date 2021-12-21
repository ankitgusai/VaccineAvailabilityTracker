package com.ankit.vaccineavailabilitytracker.activities

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.lifecycle.AndroidViewModel
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.ankit.vaccineavailabilitytracker.CowinAPIWorker
import com.ankit.vaccineavailabilitytracker.R
import com.ankit.vaccineavailabilitytracker.SpManager
import com.ankit.vaccineavailabilitytracker.databinding.ActivityMainBinding
import com.ankit.vaccineavailabilitytracker.apis.retrieveVaccinationLocationData
import com.trello.rxlifecycle4.android.lifecycle.kotlin.bindToLifecycle
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Main (and only) activity.
 *
 * Shows basic ui to configure repeatative checks to vaccine slots.
 *
 * This uses Android jetpoack lifecycle APIs to provide the view model segregation.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mBinding: ActivityMainBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_main)

        //model initialisation with the jetpack lifecycle apis and binding view model with UI.
        val viewModel by viewModels<MainActivityViewModel>()
        mBinding.data = viewModel

        //Only UI event, forwarded to VM.
        mBinding.editTextTextPersonName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                viewModel.inputSendCallFromKeyboard.onNext("")
                true
            }
            false
        }

        //View model events, using the Rxlifecycle utility. Its bit like Livedata but more flexible.
        // Activity acts on events, while logic is modeled in the view model

        viewModel.outputGoToCowinLoginPage
            .bindToLifecycle(this)
            .subscribe {
                startActivity(it)
            }
        viewModel.outputShowInvalidPin
            .bindToLifecycle(this)
            .subscribe {
                Toast.makeText(applicationContext, "Please enter valid pin!", Toast.LENGTH_LONG)
                    .show()
            }
        viewModel.outputServerNotResponding
            .bindToLifecycle(this)
            .subscribe {
                Toast.makeText(
                    applicationContext,
                    "Cowin server not responding!",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}

/**
 * View Model for Main Activity
 */
class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    //Basic initialisation
    //TODO could do with Dependency Injection
    private val spManager = SpManager(application)
    private val formatter = SimpleDateFormat("dd-MMM-yyyy hh:mm aa", Locale.US).format(Date())

    //data binding stuff
    val isTrackingActive = ObservableBoolean()
    val pinCode = ObservableField<String>()
    val startedOn = ObservableField<String>()
    val lastChecked = ObservableField<String>()
    val totalQueries = ObservableField<String>()
    val consolidatedMessageFromLastCheck = ObservableField<Spanned>()
    val isProcessing = ObservableBoolean()

    /*
    * Event triggers. input stands for source, output stands for event culmination.
     */

    /**
     * event from UI to check vaccine availability NOW.
     */
    val inputCheckNowClicked: PublishSubject<String> = PublishSubject.create<String>()

    /**
     * event from keyboard
     */
    val inputSendCallFromKeyboard: PublishSubject<String> = PublishSubject.create<String>()

    /**
     * output event for Activity.
     */
    val outputShowInvalidPin: PublishSubject<String> = PublishSubject.create<String>()

    val outputServerNotResponding: PublishSubject<String> = PublishSubject.create<String>()

    /**
     * Event from UI
     */
    val inputGoToCowinLoginPage: PublishSubject<String> = PublishSubject.create<String>()

    /**
     * above event chained for output(very neat IMO)
     */
    val outputGoToCowinLoginPage: Observable<Intent> = inputGoToCowinLoginPage
        .map { Intent(Intent.ACTION_VIEW, Uri.parse("https://selfregistration.cowin.gov.in")) }
        .filter { it.resolveActivity(application.packageManager) != null }

    /**
     * event from UI
     */
    val inputStopTracking: PublishSubject<String> = PublishSubject.create<String>()

    /**
     * The tracking chain, triggered by user from UI
     *
     * this handles both success and failure cases.
     */
    private val outputStartTracking = inputSendCallFromKeyboard
        //basic sanity checks
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
        //side event, sets some flags in shared preference and sent work manager repetitive request
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

    /**
     * continues above chain, but segregated from it because it can be triggered by other event half way through.
     *
     * At the end of the chain, data will be ready to consume, or errors handled in-between
     */
    private val currentStatusCheck: Observable<String> =
        //two event can trigger this sequence.
        PublishSubject.merge(outputStartTracking, inputCheckNowClicked)
            .doOnNext {
                isProcessing.set(true)
            }
            .observeOn(Schedulers.io())
            .filter {
                try {
                    //API call, synchronous, we are on Schedulers.io() so no worries.
                    retrieveVaccinationLocationData(application)
                    return@filter true
                } catch (e: Exception) {
                    e.printStackTrace()
                    outputServerNotResponding.onNext("Cowin server not responding...")
                    return@filter false
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
                //back to main thread
            .doOnNext {
                isProcessing.set(false)
            }


    // View model init, doesn't link to any of Activity lifecyle.

    init {

        //basic flag setup for data bidning
        isTrackingActive.set(!TextUtils.isEmpty(spManager.pinCode))
        setData()

        //subscribing to some events here as well, to update view model for data binding.
        currentStatusCheck.subscribe {
            setData()
        }

        //stopping chronic job does not require fancy action, so just doing the cleaning work here.
        inputStopTracking.subscribe {
            spManager.clearAll()
            isTrackingActive.set(false)

            // work manager clearing repetitive request.
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
