/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

/*
 * Portions of this file derived from Google's example code,
 * subject to the following:
 *
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dwallach.calwatch

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Bundle
import android.support.wearable.provider.WearableCalendarContract
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowInsets

import java.lang.ref.WeakReference
import java.util.Observable
import java.util.Observer

/**
 * Drawn heavily from the Android Wear SweepWatchFaceService example code.
 */
class CalWatchFaceService : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        engineRef = WeakReference(Engine())
        return engineRef!!.get()
    }

    inner class Engine : CanvasWatchFaceService.Engine(), Observer {
        private var clockFace: ClockFace? = null
        private var clockState: ClockState? = null
        private var calendarFetcher: CalendarFetcher? = null

        private fun subSecondRefreshNeeded(): Boolean {
            var result = false

            // if the second-hand is supposed to be rendered and we're not in ambient mode
            if (clockState != null) {
                result = clockState!!.showSeconds
            }

            if (clockFace != null) {
                result = result && !clockFace!!.ambientMode
            }

            return result
        }

        fun calendarPermissionUpdate() {
            initCalendarFetcher()
        }

        private fun initCalendarFetcher() {
            Log.v(TAG, "initCalendarFetcher")
            if (calendarFetcher != null) {
                calendarFetcher!!.kill()
                calendarFetcher = null
            }

            val permissionGiven = CalendarPermission.check(this@CalWatchFaceService)
            if (!clockState!!.calendarPermission && permissionGiven) {
                // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
                // Nonetheless, paranoia.
                Log.e(TAG, "we've got permission, need to update the clockState")
                clockState!!.calendarPermission = true
            }

            if (!permissionGiven) {
                // If this succeeds, then it will call calendarPermissionUpdate, which will asynchronously
                // call back to initCalendarFetcher(). That's why we're returning right after this.

                // The "firstTimeOnly" bit here is what keeps this from going into infinite recursion.
                // We'll only launch the activity in this instance if we've never asked the user before.

                // The onTap handler will ask, no matter what, if we don't have permission.

                Log.v(TAG, "no calendar permission given, launching first-time activity to ask")
                PermissionActivity.kickStart(this@CalWatchFaceService, true)
                return
            }

            calendarFetcher = CalendarFetcher(this@CalWatchFaceService, WearableCalendarContract.Instances.CONTENT_URI, WearableCalendarContract.AUTHORITY)
        }

        /**
         * Callback from ClockState if something changes, which means we'll need to redraw.
         */
        override fun update(observable: Observable, data: Any) {
            invalidate()
        }

        override fun onCreate(holder: SurfaceHolder?) {
            Log.d(TAG, "onCreate")

            super.onCreate(holder)

            // announce our version number to the logs
            VersionWrapper.logVersion(this@CalWatchFaceService)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@CalWatchFaceService).setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT).setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(false).setStatusBarGravity(Gravity.CENTER).setHotwordIndicatorGravity(Gravity.CENTER_HORIZONTAL) // not particularly precise, but seems reasonable
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR or WatchFaceStyle.PROTECT_STATUS_BAR).setPeekOpacityMode// the features below were added in Wear 5.1 (maybe 5.0?) and seem worth tweaking
            (WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT).setShowUnreadCountIndicator(true).setAcceptsTapEvents// we need tap events for permission requests
            (true).build())

            XWatchfaceReceiver.pingExternalStopwatches(this@CalWatchFaceService)
            BatteryWrapper.init(this@CalWatchFaceService)
            val resources = this@CalWatchFaceService.resources


            if (resources == null) {
                Log.e(TAG, "no resources? not good")
            }

            if (clockFace == null) {
                clockFace = ClockFace()
                val emptyCalendar = BitmapFactory.decodeResource(this@CalWatchFaceService.resources, R.drawable.empty_calendar)
                clockFace!!.setMissingCalendarBitmap(emptyCalendar)
            }

            clockState = ClockState.getSingleton()
            clockState!!.addObserver(this) // callbacks if something changes

            WearReceiverService.kickStart(this@CalWatchFaceService)
            CalendarPermission.init(this@CalWatchFaceService)

            initCalendarFetcher()

        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)

            val lowBitAmbientMode = properties!!.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            val burnInProtection = properties.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
            clockFace!!.setAmbientLowBit(lowBitAmbientMode)
            clockFace!!.setBurnInProtection(burnInProtection)
            Log.d(TAG, "onPropertiesChanged: low-bit ambient = $lowBitAmbientMode, burn-in protection = $burnInProtection")
        }

        override fun onTimeTick() {
            super.onTimeTick()
            //            Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());

            // this happens exactly once per minute; we're redrawing more often than that,
            // regardless, but this also provides a backstop if something is busted or buggy,
            // so we'll keep it in.
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)

            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode)
            clockFace!!.ambientMode = inAmbientMode

            // If we just switched *to* ambient mode, then we've got some FPS data to report
            // to the logs. Otherwise, we're coming *back* from ambient mode, so it's a good
            // time to reset the counters.
            if (inAmbientMode)
                TimeWrapper.frameReport()
            else
                TimeWrapper.frameReset()

            invalidate()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            clockFace!!.setMuteMode(interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE)
            invalidate()
        }

        private var drawCounter: Long = 0
        private var oldHeight: Int = 0
        private var oldWidth: Int = 0

        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            //                Log.v(TAG, "onDraw");
            drawCounter++

            val width = bounds!!.width()
            val height = bounds.height()

            if (width != oldWidth || height != oldHeight) {
                oldWidth = width
                oldHeight = height
                clockFace!!.setSize(width, height)
            }

            try {
                // clear the screen
                canvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                TimeWrapper.update() // fetch the time
                clockFace!!.drawEverything(canvas)
            } catch (t: Throwable) {
                if (drawCounter % 1000 == 0L)
                    Log.e(TAG, "Something blew up while drawing", t)
            }

            // Draw every frame as long as we're visible and doing the sweeping second hand,
            // otherwise the timer will take care of it.
            if (isVisible && subSecondRefreshNeeded())
                invalidate()
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> // if we don't have calendar permission, then that means we're interpreting
                    // watchface taps as permission requests.

                    // TODO be more specific about the x,y coordinates, like check if they're on the right side of the screen
                    if (clockState != null && !clockState!!.calendarPermission)
                        PermissionActivity.kickStart(this@CalWatchFaceService, false)
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                }
            }// user lifted their finger, "cancelling" the tap?
            // user lifted their finger, I guess?
        }


        override fun onDestroy() {
            Log.v(TAG, "onDestroy")

            if (calendarFetcher != null)
                calendarFetcher!!.kill()

            if (clockState != null) {
                clockState!!.deleteObserver(this)
                clockState = null
            }

            if (clockFace != null) {
                clockFace!!.kill()
                clockFace = null
            }

            super.onDestroy()
        }

        override fun onPeekCardPositionUpdate(bounds: Rect?) {
            super.onPeekCardPositionUpdate(bounds)
            Log.d(TAG, "onPeekCardPositionUpdate: " + bounds + " (" + bounds!!.width() + ", " + bounds.height() + ")")

            clockFace!!.setPeekCardRect(bounds)

            invalidate()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            val isRound = insets.isRound
            val chinSize = insets.systemWindowInsetBottom

            Log.v(TAG, "onApplyWindowInsets (round: $isRound), (chinSize: $chinSize)")

            clockFace!!.setRound(isRound)
            clockFace!!.setMissingBottomPixels(chinSize)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)


            if (visible) {
                invalidate()
            }

            // If we just switched *to* not visible mode, then we've got some FPS data to report
            // to the logs. Otherwise, we're coming *back* from invisible mode, so it's a good
            // time to reset the counters.
            if (!visible)
                TimeWrapper.frameReport()
            else
                TimeWrapper.frameReset()

        }
    }

    companion object {
        private val TAG = "CalWatchFaceService"

        private var engineRef: WeakReference<Engine>? = null

        val engine: Engine
            get() = engineRef!!.get()
    }
}
