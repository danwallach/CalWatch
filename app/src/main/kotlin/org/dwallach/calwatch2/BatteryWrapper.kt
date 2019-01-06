/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager.*
import org.jetbrains.anko.*
import java.lang.ref.WeakReference

/**
 * This class wraps all of our interaction with Android's BatteryManager. In particular, we only
 * want to make infrequent request to Android for the level of the battery, but we want to support
 * very frequent queries to this wrapper class.
 */
object BatteryWrapper : AnkoLogger {
    private var contextRef = WeakReference<Context>(null)

    var isCharging: Boolean = false
        private set
    var batteryPct: Float = 1.0f
        private set

    fun fetchStatus() {
        val context = contextRef.get() ?: return // no context, can't do anything

        // and now, some code for battery measurement, largely stolen from the
        // official docs.
        // http://developer.android.com/training/monitoring-device-state/battery-monitoring.html

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

            val intent = context.registerReceiver(null, ifilter)
            if (intent == null) {
                warn("Failed to get intent from registerReceiver!")
                return
            }

            // For whatever reason, Kotlin is failing to infer that intent is not null here,
            // but adding the type cast seems to solve the problem. Shouldn't happen.
            with(intent as Intent) {
                // Are we charging / charged?
                isCharging = when (getIntExtra(EXTRA_STATUS, -1)) {
                    BATTERY_STATUS_CHARGING, BATTERY_STATUS_FULL -> true
                    else -> false
                }

                // How are we charging?
                //            val chargePlug = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                //            val usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB
                //            val acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC

                val level = getIntExtra(EXTRA_LEVEL, -1)
                val scale = getIntExtra(EXTRA_SCALE, -1)

                batteryPct = level / scale.toFloat()
            }
        } catch (throwable: Throwable) {
            // if something fails, we really don't care; whatever value as in batteryPct from
            // last time is just fine for the next time
        }
    }

    fun init(context: Context) {
        info("init")
        contextRef = WeakReference(context)
        fetchStatus()
    }
}
