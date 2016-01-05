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

/**
 * This class wraps all of our interaction with Android's BatteryManager. In particular, we only
 * want to make infrequent request to Android for the level of the battery, but we want to support
 * very frequent queries to this wrapper class.
 */
class BatteryWrapper private constructor(
        // yes, we're holding a context live, and that's generally considered a bad thing, but the
        // BatteryWrapper is only held alive by the activity. If it goes away, so does the context,
        // so no leakage issues here to worry about. The logic in init() that tries to deal with
        // this might be unnecessarily redundant, but it's paranoia / defensive engineering based on
        // ancient history, where it seemed like init() got called more than once.
        private val context: Context) {

    var isCharging: Boolean = false
        private set
    var batteryPct: Float = 1.0f
        private set


    fun fetchStatus(): Unit {
        // and now, some code for battery measurement, largely stolen from the
        // official docs.
        // http://developer.android.com/training/monitoring-device-state/battery-monitoring.html

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)

            // Are we charging / charged?
            val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // How are we charging?
            //            val chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            //            val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
            //            val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

            val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            batteryPct = level / scale.toFloat()
        } catch (throwable: Throwable) {
            // if something fails, we really don't care; whatever value as in batteryPct from
            // last time is just fine for the next time
        }
    }

    companion object {
        private val TAG = "BatteryWrapper"

        private var singleton: BatteryWrapper? = null

        fun init(context: Context) {
            if (singleton == null) {
                singleton = BatteryWrapper(context)
            }

            if (context !== (singleton?.context ?: null)) {
                Log.v(TAG, "Hmm, a new context")
                singleton = BatteryWrapper(context)
            }
            singleton?.fetchStatus()
        }

        fun getWrapper(): BatteryWrapper? {
            if (singleton == null) {
                Log.e(TAG, "BatteryWrapper not initialized properly")
            }

            return singleton
        }
    }
}
