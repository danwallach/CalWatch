/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.jetbrains.anko.*

class WearSender(context: Context): AnkoLogger {
    fun sendAllToWatch() {
        try {
            val wireBytesToSend = ClockState.getProtobuf()
            if (wireBytesToSend.size == 0) return

            verbose { "preparing event list for transmission, length(${wireBytesToSend.size} bytes)" }

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
                        info { "Data item set: ${it.dataItem.uri}" }
                    } else {
                        error { "Data item failed? ${it.status.toString()}" }
                    }
                }
            }

        } catch (throwable: Throwable) {
            error("couldn't manage to send to the watch; not a big deal", throwable)
        }
    }

    init {
        verbose("init!")
        GoogleApi.connect(context) {
            verbose { "Ready" }
            sendAllToWatch()
        }
    }
}
