package org.dwallach.calwatch;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Created by dwallach on 9/1/14.
 */
public class BatteryMonitor {
    private static final String TAG = "BatteryMonitor";

    private Context context;
    private int status;
    private boolean isCharging;
    private int chargePlug;
    private boolean usbCharge;
    private boolean acCharge;
    private float batteryPct;

    private static BatteryMonitor singleton;


    private BatteryMonitor(Context context) {
        this.context = context;
        singleton = this;
    }

    public static void init(Context context) {
        if(singleton == null) {
            singleton = new BatteryMonitor(context);
            singleton.fetchStatus();
        } else if (context != singleton.context) {
            Log.v(TAG, "Hmm, a new context");
            singleton.context = context;
        }
    }

    public static BatteryMonitor getSingleton() {
        if(singleton == null)
            Log.e(TAG, "BatteryMonitor not initialized properly");

        return singleton;
    }

    void fetchStatus() {
        // and now, some code for battery measurement, largely stolen from the
        // official docs.
        // http://developer.android.com/training/monitoring-device-state/battery-monitoring.html

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        // Are we charging / charged?
        status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);

        // How are we charging?
        chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        usbCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_USB;
        acCharge = chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        batteryPct = level / (float)scale;
    }

    public float getBatteryPct() {
        return batteryPct;
    }

    public boolean getIsCharging() {
        return isCharging;
    }
}
