package org.dwallach.calwatch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * We need a separate activity for the sole purpose of requesting permissions.
 */
class PermissionActivity : Activity() {

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        Log.v(TAG, "onRequestPermissionsResult")
        CalendarPermission.handleResult(requestCode, permissions, grantResults)
        val engine = CalWatchFaceService.engine
        engine.calendarPermissionUpdate()
        Log.v(TAG, "finishing PermissionActivity")
        this.finish() // we're done, so this shuts everything down
    }

    public override fun onStart() {
        super.onStart()

        Log.v(TAG, "starting PermissionActivity")

        CalendarPermission.request(this)
    }

    companion object {
        private val TAG = "PermissionActivity"

        /**
         * Call this to launch the wear permission dialog.
         */
        fun kickStart(context: Context, firstTimeOnly: Boolean) {
            Log.v(TAG, "kickStart")

            if (firstTimeOnly && CalendarPermission.getNumRequests() > 0) return  // don't bug the user!

            val activityIntent = Intent(context, PermissionActivity::class.java)
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(activityIntent)
        }
    }
}
