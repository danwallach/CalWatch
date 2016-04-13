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
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.wearable.Wearable
import org.jetbrains.anko.*

/**
 * Unified support for dealing with the Google API client. Note that we're hard-coding the Api's that
 * we're adding. If you're using this in some other app, you'll either want to edit the calls to the
 * builder, or otherwise generalize this class.
 */

object GoogleApi: GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, AnkoLogger {
    var client: GoogleApiClient? = null
      private set
    private var successFunc: ()->Unit = {}
    private var failureFunc: ()->Unit = {}

    /**
     * Call this to initialize the connection to the Google API, along with two optional lambdas
     * for success and failure, providing callbacks if you want to do something when those events
     * occur. The resulting GoogleApiClient value can be found in the [client] field.
     */
    fun connect(context: Context, success: ()->Unit = {}, failure: ()->Unit = {}) {
        if (client == null) {
            verbose("Trying to connect")
            val lClient = GoogleApiClient
                    .Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .addApi(Fitness.HISTORY_API)
//                    .useDefaultAccount()
//                    .addScope(Scope(Scopes.FITNESS_ACTIVITY_READ))
                    .build()
            lClient.connect()

            client = lClient

            successFunc = success
            failureFunc = failure
        } else {
            verbose("Already connected")
            successFunc()
        }
    }

    /**
     * Call this to tear down the connection to the Google API.
     */
    fun close() {
        // we're going to eat any errors that happen here -- clients don't need to know or care
        try {
            verbose("disconnecting")
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
        verbose("Connected!")
        successFunc()
    }

    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

        // Apparently unrelated to connections with the phone.

        verbose("suspended!")
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
