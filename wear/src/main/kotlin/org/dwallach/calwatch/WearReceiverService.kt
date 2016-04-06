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
import android.util.Base64

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

import fr.nicolaspomepuy.androidwearcrashreport.wear.CrashReporter
import org.jetbrains.anko.*

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 */
class WearReceiverService : WearableListenerService(), AnkoLogger {
    init {
        verbose("starting listening service")
    }

    private fun newEventBytes(eventBytes: ByteArray) {
        verbose { "newEventBytes: ${eventBytes.size}" }

        ClockState.setProtobuf(eventBytes)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        verbose("service starting!")

        GoogleApi.connect(this, Wearable.API)

        // Nicholas Pomepuy's crash reporting library claims to be able to pass things
        // going kaboom all the way out to the Play Store for us. Let's see if it works.
        CrashReporter.getInstance(this).start()

        // load any saved data while we're waiting on the phone to give us fresh data
        if (ClockState.wireInitialized) {
            verbose("clock state already initialized, no need to go to saved prefs")
        } else {
            loadPreferences()
        }

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return Service.START_STICKY
    }

    override fun onDataChanged(dataEvents: DataEventBuffer?) {
        if(dataEvents == null) {
            error("onDataChanged with no data?!")
            return
        }

        debug("onDataChanged")
        dataEvents
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .map { it.dataItem }
                .forEach {
                    debug { "--> item found: ${it.toString()}" }
                    if (it.uri.path.compareTo(Constants.SettingsPath) == 0) {
                        val dataMap = DataMapItem.fromDataItem(it).dataMap
                        val eventbuf = dataMap.getByteArray(Constants.DataKey)
                        debug { "----> it's an event for us, nbytes: ${eventbuf.size}" }

                        if(eventbuf != null) {
                            newEventBytes(eventbuf)
                            savePreferences(eventbuf) // save this for subsequent restarts
                        }
                    }
                }
    }

    override fun onCreate() {
        super.onCreate()

        verbose("onCreate!")
        GoogleApi.connect(this, Wearable.API) // overkill or paranoia? no big deal to try again
    }

    /**
     * Take a serialized state update and commit it to stable storage.
     * @param eventBytes protobuf-serialed WireUpdate (typically received from the phone)
     */
    fun savePreferences(eventBytes: ByteArray?) {
        // if there's not enough state there to be real, then don't save it
        if (eventBytes == null || eventBytes.size < 1)
            return

        verbose("savePreferences: ${eventBytes.size} bytes")
        getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).edit().apply {
            // TODO there's no reason to save state like this any more; maybe we could merge with mobile/PreferencesHelper

            // Kinda sad that we need to base64-encode our state before we save it, but the SharedPreferences
            // interface doesn't allow for arbitrary arrays of bytes. So yeah, base64-encoded,
            // protobuf-encoded, WireUpdate structure, which itself has a handful of ints and a
            // variable-length array of WireEvents, which are themselves just a bunch of long's.
            putString("savedState", Base64.encodeToString(eventBytes, Base64.DEFAULT))

            if (!commit())
                warn("savePreferences commit failed ?!")
        }
    }

    /**
     * load saved state, if it's present, and use it to initialize the watchface.
     */
    fun loadPreferences() {
        verbose("loadPreferences")

        val savedState = getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).getString("savedState", "")

        if (savedState.length > 0) {
            try {
                newEventBytes(Base64.decode(savedState, Base64.DEFAULT))
            } catch (e: IllegalArgumentException) {
                error("failed to decode base64 saved state", e)
            }
        }
    }

    companion object: AnkoLogger {
        private var running = false
//        var singleton: WearReceiverService

        fun kickStart(context: Context) {
            verbose("kickStart")
            // start the calendar service, if it's not already running
            if (!running) {
                verbose("launching WearReceiverService via intent")
                running = true
                context.startService<WearReceiverService>()
            }
        }
    }
}