/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private val TAG = "CalendarPermission"

/** Deal with all the run-time permission machinery. */
object CalendarPermission {
    private const val INTERNAL_PERM_REQUEST_CODE = 31337

    /**
     * The number of permission requests ever made by CalWatch, maintained persistently
     * in the Android shared preferences.
     */
    var numRequests = -1
        private set

    /** Call at startup time. */
    fun init(context: Context) {
        numRequests =
            context.getSharedPreferences(Constants.PREFS_KEY, Context.MODE_PRIVATE).getInt("permissionRequests", 0)
    }

    /** Check whether we have permission to access the calendar. */
    fun check(context: Context): Boolean {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        Log.v(TAG, "calendar permissions check: $result (granted = ${PackageManager.PERMISSION_GRANTED})")

//        val result2 = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
//        verbose { "body sensor permissions check: $result2 (granted = ${PackageManager.PERMISSION_GRANTED})" }

        return result == PackageManager.PERMISSION_GRANTED
    }

    /** Check whether we've already asked, so we're walking on thin ice. */
    fun checkAlreadyAsked(activity: Activity): Boolean {
        val result = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
        Log.v(TAG, "previous calendar checks performed#$numRequests, shouldShowRationale=$result")
        return result
    }

    /** Make the permission request, but not if the user has already said no once. */
    fun requestFirstTimeOnly(activity: Activity) {
        if (!checkAlreadyAsked(activity))
            request(activity)
    }

    /** Request permission to access the calendar. */
    fun request(activity: Activity) {
        if (!check(activity)) {
            numRequests++
            Log.v(TAG, "this will be check #$numRequests")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_CALENDAR), // Manifest.permission.BODY_SENSORS),
                INTERNAL_PERM_REQUEST_CODE
            )

            with(activity.getSharedPreferences("org.dwallach.calwatch.prefs", Activity.MODE_PRIVATE).edit()) {
                putInt("permissionRequests", numRequests)

                if (!commit())
                    Log.v(TAG, "savePreferences commit failed ?!")
            }
        }
    }

    /** Deal with the activity callback when permissions are granted or denied. */
    fun handleResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == INTERNAL_PERM_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "calendar permission granted!")
                ClockState.calendarPermission = true
            } else {
                Log.v(TAG, "calendar permission denied!")
                ClockState.calendarPermission = false
            }
        } else {
            Log.e(TAG,
                "weird permission result: code(%d), perms(%s), results(%s)"
                    .format(requestCode, permissions.asList().toString(), grantResults.asList().toString())
            )
        }
    }
}
