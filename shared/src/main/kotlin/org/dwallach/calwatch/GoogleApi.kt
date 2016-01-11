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
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.Wearable

/**
 * Unified support for dealing with the Google API client
 */

object GoogleApi: GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private const val TAG = "GoogleApi"
    var client: GoogleApiClient? = null
      private set
    private var successFunc: ()->Unit = {}
    private var failureFunc: ()->Unit = {}

    /**
     * Call this to initialize the connection to the Google API, along with two lambdas for
     * what do to when things are successful.
     */
    fun initGoogle(context: Context, api: Api<out Api.ApiOptions.NotRequiredOptions>, success: ()->Unit = {}, failure: ()->Unit = {}) {
        if (client == null) {
            Log.v(TAG, "Trying to connect to GoogleApi")
            val lClient = GoogleApiClient
                    .Builder(context)
                    .addApi(api)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build()
            lClient.connect()

            client = lClient

            successFunc = success
            failureFunc = failure
        }
    }

    /**
     * Call this to tear down the connection to the Google API. Apparently not particularly necessary.
     */
    fun closeGoogle() {
        try {
            Log.v(TAG, "cleaning up Google API")
            val lClient = client
            if (lClient != null && lClient.isConnected) {
                lClient.disconnect()
            }
        } finally {
            client = null
        }
    }


    override fun onConnected(connectionHint: Bundle?) {
        // Apparently unrelated to connections with the phone.
        Log.v(TAG, "Connected to Google Api Service")
        successFunc()
    }

    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

        // Apparently unrelated to connections with the phone.

        Log.v(TAG, "suspended connection!")
        failureFunc()
        closeGoogle()
    }

    override fun onConnectionFailed(result: ConnectionResult?) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.

        // Apparently unrelated to connections with the phone.

        Log.v(TAG, "lost connection!")
        failureFunc()
        closeGoogle()
    }
}
