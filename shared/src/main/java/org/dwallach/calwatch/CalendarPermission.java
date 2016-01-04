package org.dwallach.calwatch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.Arrays;
import java.util.Observable;

/**
 * Deal with all the Marshmallow permission machinery.
 */
public class CalendarPermission {
    private final static String TAG = "CalendarPermission";
    private final static int INTERNAL_PERM_REQUEST_CODE = 31337;

    private static int numRequests = -1;

    public static void init(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE);
        numRequests = prefs.getInt("permissionRequests", 0);
    }

    /**
     * Read by PreferencesHelper
     */
    public static int getNumRequests() {
        return numRequests;
    }

    /**
     * Set by PreferencesHelper
     */
    public static void setNumRequests(int n) {
        Log.v(TAG, "restoring memory of numRequests: " + n);
        numRequests = n;
    }

    /**
     * Check whether we have permission to access the calendar.
     */
    public static boolean check(Context context) {
        int result = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR);
        Log.v(TAG, "calendar permissions check: " + result + " (granted = " + PackageManager.PERMISSION_GRANTED + ")");

        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check whether we've already asked, so we're walking on thin ice.
     */
    public static boolean checkAlreadyAsked(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR);
    }

    /**
     * Make the permission request, but not if the user has already said no once.
     */
    public static void requestFirstTimeOnly(Activity activity) {
        if(!checkAlreadyAsked(activity))
            request(activity);
    }

    /**
     * Request permission to access the calendar.
     */
    public static void request(Activity activity) {
        if(!check(activity)) {
            numRequests++;
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CALENDAR},
                    INTERNAL_PERM_REQUEST_CODE);

            //
            // This is redundant with the updates we do in WearReceiverService (on wear) and PreferencesHelper (on mobile),
            // but we really want to remember how many requests we've made, so we're dumping this out immediately. This
            // number will be restored on startup by the usual preferences restoration in the two classes above. (Hopefuly.)
            //
            SharedPreferences prefs = activity.getSharedPreferences("org.dwallach.calwatch.prefs", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putInt("permissionRequests", numRequests);

            if(!editor.commit())
                Log.v(TAG, "savePreferences commit failed ?!");
        }
    }

    /**
     * Deal with the activity callback when permissions are granted or denied.
     */
    public static void handleResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == INTERNAL_PERM_REQUEST_CODE) {
            ClockState state = ClockState.getSingleton();
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "calendar permission granted!");
                state.setCalendarPermission(true);
            } else {
                Log.v(TAG, "calendar permission denied!");
                state.setCalendarPermission(false);
            }
        } else {
            Log.e(TAG, String.format("weird permission result: code(%d), perms(%s), results(%s)",
                    requestCode,
                    Arrays.asList(permissions).toString(),
                    Arrays.asList(grantResults).toString()));
        }
    }
}
