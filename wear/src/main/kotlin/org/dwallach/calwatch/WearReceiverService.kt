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
import com.google.android.gms.wearable.WearableListenerService

import fr.nicolaspomepuy.androidwearcrashreport.wear.CrashReporter
import org.jetbrains.anko.*

/**
 * This class pairs up with WearSender.
 */
class WearReceiverService : WearableListenerService(), AnkoLogger {
    init {
        verbose("starting listening service")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        verbose("service starting!")

        GoogleApiWrapper.startConnection(this, true) { verbose { "GoogleApi ready" } }

        // Nicholas Pomepuy's crash reporting library claims to be able to pass things
        // going kaboom all the way out to the Play Store for us. Let's see if it works.
        CrashReporter.getInstance(this).start()

        // Perhaps we'd do better to load the preferences in CalWatchFaceService
        // rather than here, but then we're dealing with the rest of the saving
        // and loading of preferences in this file, so we'll be consistent. It
        // just updates state in ClockState, which is global, so it does't especially
        // matter when we do it.
        if (PreferencesHelper.loadPreferences(this) == 0) {
            // the code below is for backward compatibility with our earlier messaging / preferences system
            val savedState = getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).getString("savedState", "")

            if (savedState.length > 0) {
                try {
                    ClockState.setProtobuf(Base64.decode(savedState, Base64.DEFAULT))
                } catch (e: IllegalArgumentException) {
                    error("failed to decode base64 saved state", e)
                }
            }
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
                            ClockState.setProtobuf(eventbuf)
                            PreferencesHelper.savePreferences(this)
                        }
                    }
                }
    }

    override fun onCreate() {
        super.onCreate()

        verbose("onCreate!")
        GoogleApiWrapper.startConnection(this, true) { verbose { "GoogleApi ready" } }
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