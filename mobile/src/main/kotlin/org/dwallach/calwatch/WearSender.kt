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

class WearSender(context: Context) {
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
                        Log.d(TAG, "Data item set: ${it.dataItem.uri}")
                    } else {
                        Log.e(TAG, "Data item failed? ${it.status.toString()}")
                    }
                }
            }

        } catch (throwable: Throwable) {
            Log.e(TAG, "couldn't manage to send to the watch; not a big deal", throwable)
        }

    }

    init {
        Log.v(TAG, "init!")
        GoogleApi.connect(context, Wearable.API, { sendAllToWatch() })
    }

    companion object {
        private const val TAG = "WearSender"
    }
}
