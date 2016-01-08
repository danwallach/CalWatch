/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object VersionWrapper {
    private const val TAG = "VersionWrapper"

    var versionString: String? = null
        private set

    fun logVersion(activity: Context) {
        try {
            val pinfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            val versionNumber = pinfo.versionCode
            val versionName = pinfo.versionName

            versionString = "Version: $versionName ($versionNumber)"

            Log.i(TAG, versionString)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "failed to get package manager!")
        }

    }
}
