package org.dwallach.calwatch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.Arrays;

/**
 * Deal with all the Marshmallow permission machinery.
 */
public class CalendarPermission {
    private final static String TAG = "CalendarPermission";
    public final static int PERM_REQUEST_CODE = 1;

    /**
     * Check whether we have permission to access the calendar.
     */
    public static boolean check(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request permission to access the calendar.
     */
    public static void request(Activity activity) {
        if(!check(activity))
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_CALENDAR},
                    PERM_REQUEST_CODE);
    }

    /**
     * Deal with the activity callback when permissions are granted or denied.
     */
    public static void handleResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == PERM_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "calendar permission granted!");
            } else {
                Log.v(TAG, "calendar permission denied!");
            }
        } else {
            Log.e(TAG, String.format("weird permission result: code(%d), perms(%s), results(%s)",
                    requestCode,
                    Arrays.asList(permissions).toString(),
                    Arrays.asList(grantResults).toString()));
        }
    }
}
