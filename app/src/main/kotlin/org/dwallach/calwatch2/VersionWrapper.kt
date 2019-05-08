/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import android.os.Build
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.error
import org.jetbrains.anko.info

/**
 * Deals with reading our version name and number from the APK.
 */
object VersionWrapper : AnkoLogger {
    fun logVersion(activity: Context) {
        try {
            val pinfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            if (Build.VERSION.SDK_INT < 28) {
                // TODO: remove this line, but can't do that until we can guarantee API >= 28
                info { "Version: ${pinfo.versionName} (${pinfo.versionCode})" }
            } else {
                info { "Version: ${pinfo.versionName} (${pinfo.longVersionCode})" }
            }
        } catch (e: Throwable) {
            error("failed to get version information!", e)
        }
    }
}
