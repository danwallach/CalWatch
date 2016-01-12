/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.os.Bundle
import android.util.Log

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearSender(private val context: Context) {
    // yes, we're hanging onto a context here, as part the phone side of sending state to the watch,
    // and yes, hanging onto contexts is frowned upon. However, this context is only held alive
    // by virtue of the instance of WearSender, which is in turn only held alive by virtue of the
    // WatchCalendarService. And that thing can and will be summarily killed by the system at any
    // time, so this context won't leak.

    fun sendAllToWatch() {
        try {
            val wireBytesToSend = ClockState.getProtobuf()
            if (wireBytesToSend.size == 0) return

            Log.v(TAG, "preparing event list for transmission, length(${wireBytesToSend.size} bytes)")

            /*
             * Useful source: http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
             * Major source: https://developer.android.com/google/auth/api-client.html
             */
            val lClient = GoogleApi.client

            if (lClient != null && lClient.isConnected) {
                val putDataMapReq = PutDataMapRequest.create(Constants.SettingsPath)
                putDataMapReq.dataMap.putByteArray(Constants.DataKey, wireBytesToSend)
                val putDataReq = putDataMapReq.asPutDataRequest()
                putDataReq.setUrgent() // because we want this stuff to propagate quickly -- new feature along with Android M
                val pendingResult = Wearable.DataApi.putDataItem(lClient, putDataReq)

                // this callback isn't strictly necessary for correctness, but it will be
                // exceptionally useful for log debugging
                pendingResult.setResultCallback {
                    if (it.status.isSuccess) {
                        Log.d(TAG, "Data item set: " + it.dataItem.uri)
                    } else {
                        Log.e(TAG, "Data item failed? " + it.status.toString())
                    }
                }
            }

        } catch (throwable: Throwable) {
            Log.e(TAG, "couldn't manage to send to the watch; not a big deal", throwable)
        }

    }

    init {
        if (singleton == null)
            singleton = this
        onCreate()
    }

    protected fun onCreate() {
        Log.v(TAG, "onCreate!")
        GoogleApi.connect(context, Wearable.API, { sendAllToWatch() })
    }

    companion object {
        private const val TAG = "WearSender"

        //    private String nodeId;

        private var singleton: WearSender? = null
    }
}
