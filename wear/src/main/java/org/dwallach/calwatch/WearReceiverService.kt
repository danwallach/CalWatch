/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

import fr.nicolaspomepuy.androidwearcrashreport.wear.CrashReporter

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 */
class WearReceiverService : WearableListenerService(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // private List<EventWrapper> eventList = null;
    // private int maxLevel = 0;
    // private int faceMode = ClockFace.FACE_TOOL;
    private var googleApiClient: GoogleApiClient? = null

    init {
        Log.v(TAG, "starting listening service")
    }

    private fun newEventBytes(eventBytes: ByteArray) {
        Log.v(TAG, "newEventBytes: " + eventBytes.size)
        val clockState = ClockState.getSingleton()
        if (clockState == null) {
            Log.e(TAG, "whoa, no ClockState yet?!")
            return
        }

        clockState.protobuf = eventBytes
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v(TAG, "service starting!")

        // why is this necessary?
        initGoogle()

        // Nicholas Pomepuy's crash reporting library claims to be able to pass things
        // going kaboom all the way out to the Play Store for us. Let's see if it works.
        CrashReporter.getInstance(this).start()

        // this also seems a reasonable place to set up the battery monitor

        BatteryWrapper.init(this)

        // and to load any saved data while we're waiting on the phone to give us fresh data

        val clockState = ClockState.getSingleton()
        if (clockState == null) {
            Log.e(TAG, "whoa, no ClockState yet?!")
        } else {
            if (clockState.wireInitialized) {
                Log.v(TAG, "clock state already initialized, no need to go to saved prefs")
            } else {
                loadPreferences()
            }
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return Service.START_STICKY
    }

    override fun onDataChanged(dataEvents: DataEventBuffer?) {
        if(dataEvents == null) {
            Log.e(TAG, "onDataChanged with no data?!")
            return
        }

        Log.d(TAG, "onDataChanged")
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                val item = event.dataItem

                Log.d(TAG, "--> item found: " + item.toString())
                if (item.uri.path.compareTo(Constants.SettingsPath) == 0) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val eventbuf = dataMap.getByteArray(Constants.DataKey)
                    Log.d(TAG, "----> it's an event for us, nbytes: " + eventbuf!!.size)

                    // the first time through, this seems to be null; weird, but at
                    // least it's easy to ignore the nullness
                    newEventBytes(eventbuf)
                    savePreferences(eventbuf) // save this for subsequent restarts
                }
            }
        }
    }

    private fun initGoogle() {
        if (googleApiClient == null) {
            Log.v(TAG, "Trying to connect to GoogleApi")
            googleApiClient = GoogleApiClient.Builder(this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build()
            googleApiClient!!.connect()
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.v(TAG, "onCreate!")
        initGoogle()
    }

    override fun onConnected(connectionHint: Bundle?) {
        // Apparently unrelated to connections with the phone.
        Log.v(TAG, "Connected to Google Api Service")
    }


    override fun onConnectionSuspended(cause: Int) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.

        // Apparently unrelated to connections with the phone.
        Log.v(TAG, "suspended connection!")
        if (googleApiClient != null && googleApiClient!!.isConnected)
            googleApiClient!!.disconnect()
        googleApiClient = null
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.

        // Apparently unrelated to connections with the phone.

        Log.v(TAG, "lost connection!")
        if (googleApiClient != null && googleApiClient!!.isConnected)
            googleApiClient!!.disconnect()
        googleApiClient = null
    }

    override fun onPeerConnected(peer: Node?) {
        Log.v(TAG, "phone is connected!, " + peer!!.displayName)
    }

    override fun onPeerDisconnected(peer: Node?) {
        Log.v(TAG, "phone is disconnected!, " + peer!!.displayName)
    }

    /**
     * Take a serialized state update and commit it to stable storage.
     * @param eventBytes protobuf-serialed WireUpdate (typically received from the phone)
     */
    fun savePreferences(eventBytes: ByteArray?) {
        // if there's not enough state there to be real, then don't save it
        if (eventBytes == null || eventBytes.size < 1)
            return

        Log.v(TAG, "savePreferences: " + eventBytes.size + " bytes")
        val prefs = getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // TODO there's no reason to save state like this any more; maybe we could merge with mobile/PreferencesHelper

        // Kinda sad that we need to base64-encode our state before we save it, but the SharedPreferences
        // interface doesn't allow for arbitrary arrays of bytes. So yeah, base64-encoded,
        // protobuf-encoded, WireUpdate structure, which itself has a handful of ints and a
        // variable-length array of WireEvents, which are themselves just a bunch of long's.
        editor.putString("savedState", Base64.encodeToString(eventBytes, Base64.DEFAULT))

        if (!editor.commit())
            Log.v(TAG, "savePreferences commit failed ?!")
    }

    /**
     * load saved state, if it's present, and use it to initialize the watchface.
     */
    fun loadPreferences() {
        Log.v(TAG, "loadPreferences")

        val prefs = getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        val savedState = prefs.getString("savedState", "")

        if (savedState.length > 0) {
            try {
                val eventBytes = Base64.decode(savedState, Base64.DEFAULT)
                newEventBytes(eventBytes)

            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "failed to decode base64 saved state: " + e.toString())
            }

        }
    }

    companion object {
        private val TAG = "WearReceiverService"
        private var running = false;
//        var singleton: WearReceiverService

        fun kickStart(context: Context) {
            Log.v(TAG, "kickStart")
            // start the calendar service, if it's not already running
            if (!running) {
                Log.v(TAG, "launching WearReceiverService via intent")
                running = true
                val serviceIntent = Intent(context, WearReceiverService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}
