/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import java.lang.ref.WeakReference

/**
 * This class wraps all of our interaction with Android's BatteryManager. In particular, we only
 * want to make infrequent request to Android for the level of the battery, but we want to support
 * very frequent queries to this wrapper class.
 */
object BatteryWrapper {
    private const val TAG = "BatteryWrapper"

    private var contextRef = WeakReference<Context>(null)

    var isCharging: Boolean = false
        private set
    var batteryPct: Float = 1.0f
        private set

    fun fetchStatus(): Unit {
        val context = contextRef.get()
        if(context == null) {
            // no context, can't do anything
            return
        }

        // and now, some code for battery measurement, largely stolen from the
        // official docs.
        // http://developer.android.com/training/monitoring-device-state/battery-monitoring.html

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(null, ifilter).apply {
                // Are we charging / charged?
                isCharging = when(getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                    else -> false
                }

                // How are we charging?
                //            val chargePlug = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                //            val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
                //            val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

                val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                batteryPct = level / scale.toFloat()
            }
        } catch (throwable: Throwable) {
            // if something fails, we really don't care; whatever value as in batteryPct from
            // last time is just fine for the next time
        }
    }

    fun init(context: Context) {
        Log.i(TAG, "init")
        contextRef = WeakReference<Context>(context)
        fetchStatus()
    }
}
