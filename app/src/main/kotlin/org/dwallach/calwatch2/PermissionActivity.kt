/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import org.jetbrains.anko.*

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
class PermissionActivity : AppCompatActivity(), AnkoLogger {
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        verbose("onRequestPermissionsResult")
        CalendarPermission.handleResult(requestCode, permissions, grantResults)

        // If there's no engine, which might happen if the watch face changes while the permission dialog
        // is up, or something equally weird, then the below line turns into a no-op, which is probably
        // the best we can do.
        CalWatchFaceService.engine?.calendarPermissionUpdate()
        verbose("finishing PermissionActivity")
        this.finish() // we're done, so this shuts everything down
    }

    override fun onStart() {
        super.onStart()

        verbose("starting PermissionActivity")
        CalendarPermission.request(this)
    }

    companion object: AnkoLogger {
        /**
         * Call this to launch the wear permission dialog.
         */
        fun kickStart(context: Context, firstTimeOnly: Boolean) {
            verbose("kickStart")

            if (firstTimeOnly && CalendarPermission.numRequests > 0) return  // don't bug the user!

            // Anko makes this much nicer than the original
            context.startActivity(context.intentFor<PermissionActivity>().newTask())
        }
    }
}
