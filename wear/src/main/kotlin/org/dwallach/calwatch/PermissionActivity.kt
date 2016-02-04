/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch

import android.app.Activity
import android.content.Context
import android.util.Log
import org.jetbrains.anko.*

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
class PermissionActivity : Activity() {
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
        private const val TAG = "PermissionActivity"

        /**
         * Call this to launch the wear permission dialog.
         */
        fun kickStart(context: Context, firstTimeOnly: Boolean) {
            Log.v(TAG, "kickStart")

            if (firstTimeOnly && CalendarPermission.numRequests > 0) return  // don't bug the user!

//            val activityIntent = Intent(context, PermissionActivity::class.java)
//            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//            context.startActivity(activityIntent)

            // new goodies with Anko
            context.startActivity(context.intentFor<PermissionActivity>().newTask())
        }
    }
}
