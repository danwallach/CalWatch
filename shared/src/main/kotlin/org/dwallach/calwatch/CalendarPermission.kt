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
import org.jetbrains.anko.*

/**
 * Deal with all the Marshmallow permission machinery.
 */
object CalendarPermission: AnkoLogger {
    private const val INTERNAL_PERM_REQUEST_CODE = 31337

    /**
     * Returns the number of permission requests ever made by CalWatch.
     */
    var numRequests = -1
        private set

    fun init(context: Context) {
        numRequests = context.getSharedPreferences(Constants.PrefsKey, Context.MODE_PRIVATE).getInt("permissionRequests", 0)
    }

    /**
     * Check whether we have permission to access the calendar.
     */
    fun check(context: Context): Boolean {
        val result = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        verbose { "calendar permissions check: $result (granted = ${PackageManager.PERMISSION_GRANTED})" }


//        val result2 = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS)
//        verbose { "body sensor permissions check: $result2 (granted = ${PackageManager.PERMISSION_GRANTED})" }

        return result == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check whether we've already asked, so we're walking on thin ice.
     */
    fun checkAlreadyAsked(activity: Activity): Boolean {
        val result = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR)
        verbose { "previous calendar checks performed#$numRequests, shouldShowRationale=$result" }
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
            verbose { "this will be check #$numRequests" }
            ActivityCompat.requestPermissions(activity,
                    arrayOf(Manifest.permission.READ_CALENDAR), // Manifest.permission.BODY_SENSORS),
                    INTERNAL_PERM_REQUEST_CODE)

            //
            // This is redundant with the updates we do in WearReceiverService (on wear) and PreferencesHelper (on mobile),
            // but we really want to remember how many requests we've made, so we're dumping this out immediately. This
            // number will be restored on startup by the usual preferences restoration in the two classes above. (Hopefuly.)
            //
            with (activity.getSharedPreferences("org.dwallach.calwatch.prefs", Activity.MODE_PRIVATE).edit()) {
                putInt("permissionRequests", numRequests)

                if (!commit())
                    verbose { "savePreferences commit failed ?!" }
            }
        }
    }

    /**
     * Deal with the activity callback when permissions are granted or denied.
     */
    fun handleResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == INTERNAL_PERM_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                verbose { "calendar permission granted!" }
                ClockState.calendarPermission = true
            } else {
                verbose { "calendar permission denied!" }
                ClockState.calendarPermission = false
            }
        } else {
            error { "weird permission result: code(%d), perms(%s), results(%s)"
                    .format(requestCode, permissions.asList().toString(), grantResults.asList().toString()) }
        }
    }
}
