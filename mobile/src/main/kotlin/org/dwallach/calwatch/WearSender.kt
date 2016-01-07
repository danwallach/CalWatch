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

class WearSender(private val context: Context) : GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // yes, we're hanging onto a context here, as part the phone side of sending state to the watch,
    // and yes, hanging onto contexts is frowned upon. However, this context is only held alive
    // by virtue of the instance of WearSender, which is in turn only held alive by virtue of the
    // WatchCalendarService. And that thing can and will be summarily killed by the system at any
    // time, so this context won't leak.

    internal var wireBytesToSend: ByteArray? = null
    private var googleApiClient: GoogleApiClient? = null

    private fun initGoogle() {
        if (googleApiClient == null) {
            Log.v(TAG, "initializing GoogleApiClient")
            //            readyToSend = false;
            googleApiClient = GoogleApiClient.Builder(context).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()
            googleApiClient!!.connect()
        }
    }

    fun sendAllToWatch() {
        try {
            val clockState = ClockState.getState()
            wireBytesToSend = clockState.protobuf

            Log.v(TAG, "preparing event list for transmission, length(" + wireBytesToSend!!.size + " bytes)")

            /*
             * Useful source: http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
             * Major source: https://developer.android.com/google/auth/api-client.html
             */

            //            if(!isActiveConnection()) return;
            if (wireBytesToSend == null) return
            if (wireBytesToSend!!.size == 0) return

            Log.v(TAG, "ready to send request")

            /*
             * essential code borrowed from WearOngoingNotificationSample
             */
            if (googleApiClient!!.isConnected) {
                val putDataMapReq = PutDataMapRequest.create(Constants.SettingsPath)
                putDataMapReq.dataMap.putByteArray(Constants.DataKey, wireBytesToSend)
                val putDataReq = putDataMapReq.asPutDataRequest()
                putDataReq.setUrgent() // because we want this stuff to propagate quickly -- new feature along with Android M
                val pendingResult = Wearable.DataApi.putDataItem(googleApiClient, putDataReq)

                // this callback isn't strictly necessary for correctness, but it will be
                // exceptionally useful for log debugging
                pendingResult.setResultCallback { result ->
                    if (result.status.isSuccess) {
                        Log.d(TAG, "Data item set: " + result.dataItem.uri)
                    } else {
                        Log.e(TAG, "Data item failed? " + result.status.toString())
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

    //    public static WearSender getSingleton() {
    //        return singleton;
    //    }

    //    private boolean isActiveConnection() {
    //        if(readyToSend) return true;
    //        Log.v(TAG, "connection inactive, retrying");
    //        initGoogle();
    //        return false;
    //    }

    protected fun onCreate() {
        Log.v(TAG, "onCreate!")
        initGoogle()
    }

    //    private boolean readyToSend = false;
    override fun onConnected(connectionHint: Bundle?) {
        Log.v(TAG, "connected to Google API!")
        // Connected to Google Play services!
        // The good stuff goes here.
        //        readyToSend = true;

        // shouldn't ever happen, but might explain the weird null pointer exceptions that
        // rarely show up in the logs
        if (googleApiClient == null) {
            Log.e(TAG, "unexpected null googleApiClient")
            closeGoogle()
            initGoogle()
            return
        }

        sendAllToWatch()
    }

    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!")
        closeGoogle()
    }

    override fun onConnectionFailed(result: ConnectionResult?) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!")
        closeGoogle()
    }

    private fun closeGoogle() {
        try {
            Log.v(TAG, "cleaning up Google API")
            //            readyToSend = false;
            if (googleApiClient != null) {
                if (googleApiClient!!.isConnected)
                    googleApiClient!!.disconnect()
            }
        } finally {
            googleApiClient = null
        }
    }

    companion object {
        private val TAG = "WearSender"

        //    private String nodeId;

        private var singleton: WearSender? = null
    }
}
