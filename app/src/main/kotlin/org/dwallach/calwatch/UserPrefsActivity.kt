/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch

import android.app.Activity
import org.jetbrains.anko.*

/**
 * We need a separate activity for the user preferences popup.
 */
class UserPrefsActivity : Activity(), AnkoLogger {
    override fun onStart() {
        super.onStart()

        verbose("starting UserPrefsActivity")
    }

    // https://developer.android.com/training/wearables/ui/wear-ui-library.html
}
