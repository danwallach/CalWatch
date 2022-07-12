/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2022 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import splitties.activities

private val TAG = "PermissionActivity"

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
class PermissionActivity : AppCompatActivity() {
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.v(TAG, "onRequestPermissionsResult")
        CalendarPermission.handleResult(requestCode, permissions, grantResults)

        // If there's no engine, which might happen if the watch face changes while the permission dialog
        // is up, or something equally weird, then the below line turns into a no-op, which is probably
        // the best we can do.
        CalWatchFaceService.engine?.calendarPermissionUpdate()
        Log.v(TAG, "finishing PermissionActivity")
        this.finish() // we're done, so this shuts everything down
    }

    override fun onStart() {
        super.onStart()

        Log.v(TAG, "starting PermissionActivity")
        CalendarPermission.request(this)
    }

    companion object {
        /** Call this to launch the wear permission dialog. */
        fun kickStart(context: Context, firstTimeOnly: Boolean) {
            Log.v(TAG, "kickStart")

            if (firstTimeOnly && CalendarPermission.numRequests > 0) return // don't bug the user!

            // TODO: find replacement for this otherwise nice Anko feature
            start<PermissionActivity>()
            context.startActivity(context.intentFor<PermissionActivity>().newTask())
        }
    }
}
