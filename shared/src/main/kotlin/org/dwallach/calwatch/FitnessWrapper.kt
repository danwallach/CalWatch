package org.dwallach.calwatch

import android.content.Context
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import org.jetbrains.anko.*
import java.util.concurrent.TimeUnit

/**
 * This class wraps the Android Fit API and provides a daily step count value, which we
 * can then use as part of the watchface rendering.
 */

object FitnessWrapper: AnkoLogger {
    private var stepCount: Int = 0

    private var lastSampleTime = 0L
    private var inProgress = false
    private var cachedResultCounter = 0
    private var successfulResults = 0
    private var failedResults = 0
    private var inProgressCounter = 0
    private var noGoogleApiCounterNull = 0
    private var noGoogleApiCounterConnecting = 0
    private var noGoogleApiCounterConnected = 0

    fun getStepCount(): Int {
        loadStepCount() // start possible asynchronous load
        return stepCount // return older result
    }

    fun report() {
        info { "steps: ${stepCount}, prior cached: ${cachedResultCounter}, successful: ${successfulResults}," +
                " noGoogleApi: ${noGoogleApiCounterNull}/${noGoogleApiCounterConnecting}/${noGoogleApiCounterConnected}," +
                " failed: ${failedResults}, in-progress: ${inProgressCounter}" }
    }

    private fun loadStepCount() {
        if(inProgress) {
            inProgressCounter++
            return
        }

        val client = GoogleApiWrapper.client
        if(client == null)  {
            noGoogleApiCounterNull++
            return // nothing to do!
        }
        if(client.isConnecting) {
            noGoogleApiCounterConnecting++
            return // nothing to do!
        }
        if(!client.isConnected) {
            noGoogleApiCounterConnected++
            return // nothing to do!
        }

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
        if(currentTime - lastSampleTime < 30000) {
            cachedResultCounter++
            return
        }
        lastSampleTime = currentTime

        //
        // Example code, via StackOverflow
        // http://stackoverflow.com/questions/35695336/google-fit-history-api-readdailytotal-non-static-method-in-static-context/

        // Converting this to Kotlin, the callback never actually happened, so we switched to a translation
        // of the official code from Google which uses await, except we do it on an async thread. Because Kotlin.
        // https://developers.google.com/android/reference/com/google/android/gms/fitness/HistoryApi
        //
        inProgress = true
        report()

        async() {
            var newStepCount: Int = stepCount

            val result = Fitness.HistoryApi.readDailyTotal(client, DataType.TYPE_STEP_COUNT_DELTA).await(10, TimeUnit.SECONDS)

            if (result.status.isSuccess) {
                val totalSet = result.total
                if (totalSet != null) {
                    newStepCount = if (totalSet.isEmpty) 0 else totalSet.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                    successfulResults++
                    verbose { "Step Count: ${stepCount}" }
                } else {
                    failedResults++
                    debug { "No total set; no step count" }
                }
            } else {
                failedResults++
                debug { "Failed callback: ${result.status.toString()}" }
            }

            uiThread {
                stepCount = newStepCount
                inProgress = false
            }
        }
    }
}