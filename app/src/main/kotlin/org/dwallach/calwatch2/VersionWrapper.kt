/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import org.jetbrains.anko.*

/**
 * Deals with reading our version name and number from the APK.
 */
object VersionWrapper : AnkoLogger {
    fun logVersion(activity: Context) {
        try {
            val pinfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            // TODO: replace versionCode with longVersionCode, except that's only supported in API 28
            info { "Version: ${pinfo.versionName} (${pinfo.versionCode})" }
        } catch (e: Throwable) {
            error("failed to get version information!", e)
        }
    }
}
