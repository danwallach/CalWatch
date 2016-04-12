/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.GoogleApiClient
import org.jetbrains.anko.*

/**
 * Unified support for dealing with the Google API client.
 */

object GoogleApi: GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, AnkoLogger {
    var client: GoogleApiClient? = null
      private set
    private var successFunc: ()->Unit = {}
    private var failureFunc: ()->Unit = {}

    private var apiSet: Set<Api<out Api.ApiOptions.NotRequiredOptions>> = emptySet()

    /**
     * Different parts of your code will want to use different GoogleApis. Add them here,
     * then call [connect]. Thereafter, the result will stick around the [client] field.
     */
    fun addApi(api: Api<out Api.ApiOptions.NotRequiredOptions>) {
        apiSet = apiSet + api
        if (client != null) close()
    }

    /**
     * Call this to initialize the connection to the Google API, along with two optional lambdas
     * for success and failure, providing callbacks if you want to do something when those events
     * occur. The resulting GoogleApiClient value can be found in the [client] field.
     */
    fun connect(context: Context, success: ()->Unit = {}, failure: ()->Unit = {}) {
        if (client == null) {
            verbose("Trying to connect to GoogleApi")
            val lClient = GoogleApiClient
                    .Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .apply { apiSet.forEach { addApi(it) } }
                    .build()
            lClient.connect()

            client = lClient

            successFunc = success
            failureFunc = failure
        }
    }

    /**
     * Call this to tear down the connection to the Google API.
     */
    fun close() {
        // we're going to eat any errors that happen here -- clients don't need to know or care
        try {
            verbose("cleaning up Google API")
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
        verbose("Connected to Google Api Service!")
        successFunc()
    }

    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

        // Apparently unrelated to connections with the phone.

        verbose("suspended connection!")
        close()
        failureFunc()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.

        // Apparently unrelated to connections with the phone.

        verbose("lost connection!")
        close()
        failureFunc()
    }
}
