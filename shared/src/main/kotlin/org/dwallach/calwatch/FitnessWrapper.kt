package org.dwallach.calwatch

import android.content.Context
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info
import org.jetbrains.anko.verbose
import org.jetbrains.anko.error

/**
 * This class wraps the Android Fit API and provides a daily step count value, which we
 * can then use as part of the watchface rendering.
 */

object FitnessWrapper: AnkoLogger {
    private var stepCount: Int = 0

    private var lastSampleTime = 0L
    private var inProgress = false
    private var initialized = false
    private var cachedResultCounter = 0
    private var successfulResults = 0
    private var failedResults = 0
    private var inProgressCounter = 0

    fun init(context: Context) {
        GoogleApi.addModifier { it
                .addApi(Fitness.HISTORY_API)
                .useDefaultAccount()
                .addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
        }
        GoogleApi.connect(context)
        initialized = true

        info { "Initialized GoogleApi for fitness" }
    }

    fun getStepCount(): Int {
        loadStepCount() // start possible asynchronous load
        return stepCount // return older result
    }

    fun report() {
        info { "steps: ${stepCount}, prior cached: {$cachedResultCounter}, successful: {$successfulResults}, failed: {$failedResults}, in-progress: ${inProgressCounter}" }
    }

    private fun loadStepCount() {
        if(inProgress) {
            inProgressCounter++
            return
        }

        if(!initialized) {
            failedResults++
            error { "GoogleApi not initialized" }
            return
        }

        val client = GoogleApi.client
        if(client == null)  {
            failedResults++
            info { "No GoogleApiClient; cannot load Fitness data" }
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
        //
        inProgress = true
        report()

        Fitness.HistoryApi.readDailyTotal(client, DataType.TYPE_STEP_COUNT_DELTA).setResultCallback {
            if(it.status.isSuccess) {
                val totalSet = it.total
                if(totalSet != null) {
                    stepCount = if (totalSet.isEmpty) 0 else totalSet.dataPoints[0].getValue(Field.FIELD_STEPS).asInt()
                    successfulResults++
                    verbose { "Step Count: %5d".format(stepCount) }
                } else {
                    failedResults++
                    debug { "No total set; no step count" }
                }
            } else {
                failedResults++
                debug { "Callback status: failed! (%s)".format(it.status.toString()) }
            }
            inProgress = false
        }
    }
}