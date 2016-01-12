/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log

/**
 * Deal with all the Marshmallow permission machinery.
 */
object CalendarPermission {
    private const val TAG = "CalendarPermission"
    private const val INTERNAL_PERM_REQUEST_CODE = 31337

    /**
     * Returns the number of permission requests ever made by CalWatch.
     */
    var numRequests = -1
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE)
        numRequests = prefs.getInt("permissionRequests", 0)
    }

    /**
     * Check whether we have permission to access the calendar.
     */
    fun check(context: Context): Boolean {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        Log.v(TAG, "calendar permissions check: $result (granted = ${PackageManager.PERMISSION_GRANTED})")

        return result == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether we've already asked, so we're walking on thin ice.
     */
    fun checkAlreadyAsked(activity: Activity): Boolean {
        val result = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
        Log.v(TAG, "previous checks performed#$numRequests, shouldShowRationale=$result")
        return result
    }

    /**
     * Make the permission request, but not if the user has already said no once.
     */
    fun requestFirstTimeOnly(activity: Activity) {
        if (!checkAlreadyAsked(activity))
            request(activity)
    }

    /**
     * Request permission to access the calendar.
     */
    fun request(activity: Activity) {
        if (!check(activity)) {
            numRequests++
            Log.v(TAG, "this will be check #" + numRequests)
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_CALENDAR),
                    INTERNAL_PERM_REQUEST_CODE)

            //
            // This is redundant with the updates we do in WearReceiverService (on wear) and PreferencesHelper (on mobile),
            // but we really want to remember how many requests we've made, so we're dumping this out immediately. This
            // number will be restored on startup by the usual preferences restoration in the two classes above. (Hopefuly.)
            //
            val prefs = activity.getSharedPreferences("org.dwallach.calwatch.prefs", Activity.MODE_PRIVATE)
            val editor = prefs.edit()

            editor.putInt("permissionRequests", numRequests)

            if (!editor.commit())
                Log.v(TAG, "savePreferences commit failed ?!")
        }
    }

    /**
     * Deal with the activity callback when permissions are granted or denied.
     */
    fun handleResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == INTERNAL_PERM_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG, "calendar permission granted!")
                ClockState.calendarPermission = true
            } else {
                Log.v(TAG, "calendar permission denied!")
                ClockState.calendarPermission = false
            }
        } else {
            Log.e(TAG, "weird permission result: code(%d), perms(%s), results(%s)"
                    .format(requestCode, permissions.asList().toString(), grantResults.asList().toString()));
        }
    }
}
