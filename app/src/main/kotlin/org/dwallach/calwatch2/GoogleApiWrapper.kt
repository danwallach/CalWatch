/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.content.Context
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.wearable.Wearable
import org.jetbrains.anko.*

/**
 * Unified support for dealing with the Google API client. Note that we're hard-coding the apis that
 * we need for CalWatch. If you're using this in some other app, you'll either want to edit the calls to the
 * builder, or otherwise generalize this class.
 */

object GoogleApiWrapper : GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, AnkoLogger {
    /**
     * The GoogleApiClient instance. Call [startConnection] to get it initialized.
     */
    var client: GoogleApiClient? = null
      private set
    private var successFunc: ()->Unit = {}
    private var failureFunc: ()->Unit = {}

    /**
     * Call this to initialize the connection to the Google API, along with two optional lambdas
     * for success and failure, providing callbacks if you want to do something when those events
     * occur. The resulting GoogleApiClient value can be found in the [client] field.
     */
    fun startConnection(context: Context, wear: Boolean = false, success: ()->Unit = {}, failure: ()->Unit = {}) {
        info { "startConnection" }
        if (client == null) {
            info { "Trying to connect" }
            val lClient = GoogleApiClient
                    .Builder(context)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .apply {
                        //
                        // Fun fact: you can get at these APIs without any permission or login
                        // on wear, but that's not true for mobile. Consequently, we're now
                        // dealing with two versions of everything.
                        //
                        if(wear) {
                            addApi(Fitness.HISTORY_API)
                            addApi(Fitness.RECORDING_API)
                            useDefaultAccount()
                        }
                    }
                    .build()
            lClient.connect() // starts asynchronous connection

            client = lClient

            successFunc = success
            failureFunc = failure
        } else {
            info { "Already connected (or in progress)" }
            success()
        }
    }

    /**
     * Call this to tear down the connection to the Google API.
     */
    fun close() {
        info { "close" }
        // we're going to eat any errors that happen here -- clients don't need to know or care
        try {
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
        info { "Connected!" }
        successFunc()
    }

    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

        // Apparently unrelated to connections with the phone.

        info { "suspended! cause: $cause" }
//        close()
        failureFunc()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.

        // Apparently unrelated to connections with the phone.

        error { "lost connection! code: ${result.errorCode}, message: ${result.errorMessage}, hasResolution? ${result.hasResolution()}" }
        close()
        failureFunc()
    }
}
