/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
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
            if (pinfo == null) {
                error { "package info was null, can't figure out version information" }
            } else {
                val versionInfo = PackageInfoCompat.getLongVersionCode(pinfo)

                val hiBits = (versionInfo shr 32) and 0xffffffff
                val loBits = versionInfo and 0xffffff

                info { "Version: ${pinfo.versionName} (versionCodeMajor: $hiBits, versionCode: $loBits)" }
            }
        } catch (e: Throwable) {
            error("failed to get version information!", e)
        }
    }
}
