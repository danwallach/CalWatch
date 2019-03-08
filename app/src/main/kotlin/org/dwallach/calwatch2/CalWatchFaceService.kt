/*
 * CalWatch / CalWatch2
 * Copyright © 2014-2019 by Dan S. Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowInsets
import java.lang.ref.WeakReference
import org.dwallach.R
import org.dwallach.complications.ComplicationWrapper
import org.dwallach.complications.ComplicationWrapper.styleComplications
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import org.jetbrains.anko.verbose
import org.jetbrains.anko.warn

/** Drawn heavily from the Android Wear SweepWatchFaceService / AnalogWatchFaceService examples. */
class CalWatchFaceService : CanvasWatchFaceService(), AnkoLogger {
    override fun onCreateEngine(): Engine {
        val engine = Engine()
        engineRef = WeakReference(engine)
        return engine
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var clockFace: ClockFace
        private var calendarFetcher: CalendarFetcher? = null

        /** Call this if there's been a status update in the calendar permissions. */
        fun calendarPermissionUpdate() {
            warn("calendarPermissionUpdate")

            val permissionGiven = CalendarPermission.check(this@CalWatchFaceService)
            if (!ClockState.calendarPermission && permissionGiven) {
                // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
                // However, we could get here after a reboot or something. So paranoia is good.
                warn("we've got permission, need to update the ClockState")
                ClockState.calendarPermission = true
            }

            if (permissionGiven)
                calendarFetcher = CalendarFetcher(this@CalWatchFaceService)
        }

        override fun onCreate(holder: SurfaceHolder?) {
            info("onCreate")

            super.onCreate(holder)

            // announce our version number to the logs
            VersionWrapper.logVersion(this@CalWatchFaceService)

            // load any saved preferences
            PreferencesHelper.loadPreferences(this@CalWatchFaceService)

            // there were a lot more choices here for Wear1; this seems to do what we want
            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@CalWatchFaceService)
                    .setAccentColor(Color.YELLOW)
                    .setStatusBarGravity(Gravity.CENTER)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_WHOLE_SCREEN)
                    .setShowUnreadCountIndicator(false) // would prefer true, but doesn't seem to work reliably
                    .setHideNotificationIndicator(false)
                    .setHideStatusBar(false)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            BatteryWrapper.init(this@CalWatchFaceService)

            val resources = this@CalWatchFaceService.resources

            if (resources == null) {
                error("no resources? not good")
            }

            clockFace = ClockFace()
            clockFace.missingCalendarDrawable = getDrawable(R.drawable.ic_empty_calendar)

            CalendarPermission.init(this@CalWatchFaceService)

            calendarPermissionUpdate()

            // Note: this is the place where we specify which complications we want and don't want.
            // We're deliberately disabling the bottom and left complications, since we draw our
            // own background, and because the left complication is replaced with our built-in day/date
            // rendering.
            ComplicationWrapper.init(this@CalWatchFaceService, this, Constants.COMPLICATION_LOCATIONS)
            styleComplications {
                setBackgroundColorActive(PaintCan.COMPLICATION_BG_COLOR)
                setBorderColorActive(PaintCan.COMPLICATION_FG_COLOR)
                setBorderColorAmbient(PaintCan.COMPLICATION_FG_COLOR)
            }
        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)

            if (properties == null) {
                info("onPropertiesChanged: empty properties?!")
                return
            }

            // Kinda amazing that you can do nested with() calls and the right thing happens!
            with(properties) {
                with(clockFace) {
                    ambientLowBit = getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
                    burnInProtection = getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
                    ComplicationWrapper.updateProperties(ambientLowBit, burnInProtection)
                    info { "onPropertiesChanged: low-bit ambient = $ambientLowBit, burn-in protection = $burnInProtection" }
                }
            }
        }

        override fun onTimeTick() {
            super.onTimeTick()

            // this happens exactly once per minute; we're redrawing more often than that,
            // regardless, but this also provides a backstop if something is busted or buggy,
            // so we'll keep it in.
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)

            info { "onAmbientModeChanged: $inAmbientMode" }
            clockFace.ambientMode = inAmbientMode
            ComplicationWrapper.updateAmbientMode(inAmbientMode)

            // If we just switched *to* ambient mode, then we've got some FPS data to report
            // to the logs. Otherwise, we're coming *back* from ambient mode, so it's a good
            // time to reset the counters.
            if (inAmbientMode)
                TimeWrapper.frameReport()
            else
                TimeWrapper.frameReset()

            Utilities.redrawEverything()
        }

        /** Called when there is updated data for a complication id. */
        override fun onComplicationDataUpdate(
            complicationId: Int,
            complicationData: ComplicationData?
        ) {
            verbose { "onComplicationDataUpdate() id: $complicationId" }

            ComplicationWrapper.updateComplication(complicationId, complicationData)
            Utilities.redrawEverything()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            clockFace.muteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE)
            invalidate()
        }

        private var drawCounter: Long = 0
        private var oldHeight: Int = 0
        private var oldWidth: Int = 0

        private fun updateBounds(width: Int, height: Int) {
            if (width != oldWidth || height != oldHeight) {
                info { "updateBounds: $width x $height" }
                oldWidth = width
                oldHeight = height
                clockFace.setSize(width, height)
                ComplicationWrapper.updateBounds(width, height)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            updateBounds(width, height)
            Utilities.redrawEverything()
        }

        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            //                Log.v(TAG, "onDraw")
            drawCounter++

            if (bounds == null || canvas == null) {
                debug("onDraw: null bounds and/or canvas")
                return
            }

            updateBounds(bounds.width(), bounds.height())

            try {
                // clear the screen
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                TimeWrapper.update() // fetch the time
                clockFace.drawEverything(canvas)

                // Draw every frame as long as we're visible and doing the sweeping second hand,
                // otherwise the timer will take care of it.
                if (isVisible && ClockState.subSecondRefreshNeeded(clockFace))
                    invalidate()
            } catch (t: Throwable) {
                if (drawCounter % 1000 == 0L)
                    error("Something blew up while drawing", t)
            }
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // user touched the screen, but we're not allowed to act yet
                }

                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // if we were animating on the "touch" part, this would tell us to give up
                }

                WatchFaceService.TAP_TYPE_TAP -> {
                    // user lifted their finger, so now we're allowed to act

                    // if we don't have calendar permission, then that means we're interpreting
                    // watchface taps as permission requests, otherwise, we're delegating to
                    // the complications library
                    if (!ClockState.calendarPermission)
                        PermissionActivity.kickStart(this@CalWatchFaceService, false)
                    else
                        ComplicationWrapper.handleTap(this@CalWatchFaceService, x, y, eventTime)
                }
            }
        }

        override fun onDestroy() {
            verbose("onDestroy")

            calendarFetcher?.kill()

            super.onDestroy()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            with(clockFace) {
                round = insets.isRound
                missingBottomPixels = insets.systemWindowInsetBottom
                verbose { "onApplyWindowInsets (round: $round), (chinSize: $missingBottomPixels)" }
            }

            Utilities.redrawEverything()
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
        private var engineRef: WeakReference<Engine>? = null

        val engine: Engine?
            get() = engineRef?.get()

        fun redraw() {
            engine?.invalidate()
        }
    }
}
