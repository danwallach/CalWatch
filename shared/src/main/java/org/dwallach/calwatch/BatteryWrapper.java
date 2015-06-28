/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Created by dwallach on 9/1/14.
 */
public class BatteryWrapper {
    private static final String TAG = "BatteryWrapper";

    private Context context;
    private boolean isCharging;
    private float batteryPct = 1.0f;

    private static BatteryWrapper singleton;


    private BatteryWrapper(Context context) {
        this.context = context;
        singleton = this;
    }

    public static void init(Context context) {
        if(singleton == null) {
            singleton = new BatteryWrapper(context);
        } else if (context != singleton.context) {
            Log.v(TAG, "Hmm, a new context");
            singleton.context = context;
        }
        singleton.fetchStatus();
    }

    public static BatteryWrapper getSingleton() {
        if(singleton == null)
            Log.e(TAG, "BatteryWrapper not initialized properly");

        return singleton;
    }

    public void fetchStatus() {
        // and now, some code for battery measurement, largely stolen from the
        // official docs.
        // http://developer.android.com/training/monitoring-device-state/battery-monitoring.html

        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            // Are we charging / charged?
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);

            // How are we charging?
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
//            boolean usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
//            boolean acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            batteryPct = level / (float) scale;
        } catch (Throwable throwable) {
            // if something fails, we really don't care; whatever value as in batteryPct from
            // last time is just fine for the next time
        }
    }

    public float getBatteryPct() {
        return batteryPct;
    }

    public boolean getIsCharging() {
        return isCharging;
    }
}
