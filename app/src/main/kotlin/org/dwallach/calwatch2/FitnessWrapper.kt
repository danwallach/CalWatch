package org.dwallach.calwatch2

import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessStatusCodes
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import org.jetbrains.anko.*

/**
 * This class wraps the Android Fit API and provides a daily step count value, which we
 * can then use as part of the watchface rendering.
 */

object FitnessWrapper: AnkoLogger {
    private var stepCount: Int = 0

    /**
     * If true, then FitnessWrapper just returns 0. If false, then it actually fetches
     * the daily step counter.
     */
    var fakeResults: Boolean = false

    private var lastSampleTime = 0L
    private var inProgress = false
    private var cachedResultCounter = 0
    private var successfulResults = 0
    private var failedResults = 0
    private var inProgressCounter = 0
    private var noGoogleApiCounterNull = 0
    private var noGoogleApiCounterConnecting = 0
    private var noGoogleApiCounterConnected = 0

    private var subscribed = false

    fun getStepCount(): Int {
        if(fakeResults)
            return 0

        loadStepCount() // start possible asynchronous load
        return stepCount // return older result
    }

    fun report() {
        info { "steps: $stepCount, prior cached: $cachedResultCounter, successful: $successfulResults, noGoogleApi: $noGoogleApiCounterNull/$noGoogleApiCounterConnecting/$noGoogleApiCounterConnected, failed: $failedResults, in-progress: $inProgressCounter" }
    }
    /**
     * Gets the GoogleApiClient, if it exists, and if it fails, increments the various
     * counters we use to track these failures without yelling about them in the logs.
     */
    private fun getGoogleApi(): GoogleApiClient? {
        val client = GoogleApiWrapper.client

        if(client == null)  {
            noGoogleApiCounterNull++
            return null // nothing to do!
        }
        if(client.isConnecting) {
            noGoogleApiCounterConnecting++
            return null // nothing to do!
        }
        if(!client.isConnected) {
            noGoogleApiCounterConnected++
            return null // nothing to do!
        }

        return client
    }

    /**
     * Connects a subscription to the step counter. Not strictly necessary, but appears to save power.
     */
    fun subscribe() {
        if(fakeResults) return

        // Kotlin translation of subscribeToSteps() as shown here
        // https://github.com/googlesamples/android-WatchFace/blob/master/Wearable/src/main/java/com/example/android/wearable/watchface/FitStepsWatchFaceService.java

        if(subscribed) return

        val client = getGoogleApi() ?: return

        Fitness.RecordingApi
                .subscribe(client, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback {
                    if(it.isSuccess) {
                        if(it.statusCode == FitnessStatusCodes.SUCCESS_ALREADY_SUBSCRIBED) {
                            info { "Existing subscription for fitness activity detected." }
                        } else {
                            info { "Fitness subscription successful." }
                            subscribed = true
                        }
                    } else {
                       error { "failed to get fitness subscription: ${it.statusMessage}" }
                    }
                }
    }

    private fun loadStepCount() {
        if(inProgress) {
            //
            // A word about atomicity:
            //
            // In a traditional multithreaded world, we wouldn't just have a boolean to track
            // that we're in progress. We'd have a mutex and/other atomicity goodies. In the
            // Android universe, we're only ever going to hit this function as part of the
            // screen-redrawing process, so we'll *always be on the UI thread*. Consequently,
            // a simple boolean does the job for us. We do use a worker thread as part of the
            // actual asynchronous query to the HistoryApi (see below), which will delay resetting
            // the inProgress boolean until it's finished and is back running on the UI thread.
            //
            inProgressCounter++
            return
        }

        subscribe() // will be a no-op, if we're already subscribed

        val client = getGoogleApi() ?: return

        //
        // We'll only resample the step counter if our data is old. How old? Quoth Google:
        //   To keep step counts consistent, subscribe to steps in the Google Fit platform
        //   from your fitness app or watch face, and then call this method every 30 seconds
        //   in interactive mode, and every 60 seconds in ambient mode.
        //   https://developers.google.com/fit/android/history
        //
        // If we're running in ambient mode, we'll only be asked to redraw the watchface once
        // a minute. Consequently, the logic here only needs to check if we're >= 30 seconds
        // behind.
        //
        // This means we'll be displaying a one-minute old reading, whenever we're in ambient
        // mode, but since we're not even vaguely trying to be precise, it doesn't really matter.
        //
        val currentTime = TimeWrapper.gmtTime
        if(currentTime - lastSampleTime < 30.seconds) {
            cachedResultCounter++
            return
        }
        lastSampleTime = currentTime

        inProgress = true

        Fitness.HistoryApi
                .readDailyTotal(client, DataType.TYPE_STEP_COUNT_DELTA)
                .setResultCallback {
                    if (it.status.isSuccess) {
                        val totalSet = it.total
                        if (totalSet != null) {
                            stepCount = if (totalSet.isEmpty) 0 else totalSet.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                            successfulResults++
                            verbose { "Step Count: $stepCount" }
                        } else {
                            failedResults++
                            debug { "No total set; no step count" }
                        }
                    } else {
                        failedResults++
                        debug { "Failed callback: ${it.status}" }
                    }
                    inProgress = false
                }
    }
}