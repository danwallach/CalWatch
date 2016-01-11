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
import android.os.IBinder
import android.util.Log

import java.util.Observable
import java.util.Observer

import fr.nicolaspomepuy.androidwearcrashreport.mobile.CrashReport

class WatchCalendarService : Service(), Observer {
    private lateinit var wearSender: WearSender

    init {
        singletonService = this
    }

    // this is called when there's something new from the calendar DB
    fun sendAllToWatch() {
        wearSender.sendAllToWatch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.v(TAG, "service starting!")

        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return Service.START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.v(TAG, "service created!")

        ClockState.addObserver(this)
        wearSender = WearSender(this)

        PreferencesHelper.loadPreferences(this)

        // Nicholas Pomepuy's crash reporting library will collect exceptions on the watch,
        // pass them along via the Data API, and they'll end up here, where we will then
        // pass them along to the Google Play Store. I'll be amazed if this works.
        CrashReport.getInstance(this).setOnCrashListener { crashInfo ->
            // Manage the crash
            Log.e(TAG, "wear-side crash detected!", crashInfo.throwable)
            CrashReport.getInstance(this@WatchCalendarService).reportToPlayStore(this@WatchCalendarService)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v(TAG, "service destroyed!")

        ClockState.deleteObserver(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.e(TAG, "onBind: we should support this")
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun update(observable: Observable?, data: Any?) {
        // somebody updated something in the clock state (new events, new display options, etc.)
        Log.v(TAG, "internal clock state changed: time to send all to the watch")
        sendAllToWatch()
    }

    companion object {
        private const val TAG = "WatchCalendarService"

        var singletonService: WatchCalendarService? = null
        private set

        fun kickStart(ctx: Context) {
            // start the calendar service, if it's not already running
            if (singletonService == null) {
                Log.v(TAG, "launching watch calendar service")
                val serviceIntent = Intent(ctx, WatchCalendarService::class.java)
                ctx.startService(serviceIntent)
            }
        }
    }
}
