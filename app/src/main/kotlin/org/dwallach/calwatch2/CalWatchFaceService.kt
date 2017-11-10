/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

package org.dwallach.calwatch2

import android.graphics.*
import android.os.Bundle
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.WindowInsets
import org.dwallach.R
import org.dwallach.complications.ComplicationWrapper
import org.jetbrains.anko.*

import java.lang.ref.WeakReference
import java.util.Observable
import java.util.Observer
import android.support.wearable.complications.ComplicationData
import org.dwallach.complications.ComplicationLocation.*

/**
 * Drawn heavily from the Android Wear SweepWatchFaceService / AnalogWatchFaceService examples.
 */
class CalWatchFaceService : CanvasWatchFaceService(), AnkoLogger {
    override fun onCreateEngine(): Engine {
        val engine = Engine()
        engineRef = WeakReference(engine)
        return engine
    }

    inner class Engine : CanvasWatchFaceService.Engine(), Observer {
        private lateinit var clockFace: ClockFace
        private var calendarFetcher: CalendarFetcher? = null

        /**
         * Call this if there's been a status update in the calendar permissions.
         */
        fun calendarPermissionUpdate() {
            initCalendarFetcher()
        }

        /**
         * Internal function for dealing with the calendar fetcher.
         */
        private fun initCalendarFetcher() {
            warn("initCalendarFetcher")

            val permissionGiven = CalendarPermission.check(this@CalWatchFaceService)
            if (!ClockState.calendarPermission && permissionGiven) {
                // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
                // However, we could get here after a reboot or something. So paranoia is good.
                warn("we've got permission, need to update the ClockState")
                ClockState.calendarPermission = true
            }

            if (!permissionGiven) {
                // If we got here, then we don't have permission to read the calendar. Bummer. The
                // only way to fix this is to create a whole new Activity from which to make the
                // request of the user.

                // If this succeeds, then it will asynchronously call calendarPermissionUpdate(), which will
                // call back to initCalendarFetcher(). That's why we're returning right after this.

                // The "firstTimeOnly" bit here is what keeps this from going into infinite recursion.
                // We'll only launch the activity in this instance if we've never asked the user before.

                // If the user says "no", they'll be given the "empty calendar" icon and can clock the
                // screen. The onTap handler will also then kickstart the permission activity.

                warn("no calendar permission given, launching first-time activity to ask")
                PermissionActivity.kickStart(this@CalWatchFaceService, true)
                return
            }

            calendarFetcher = CalendarFetcher(this@CalWatchFaceService)
        }

        /**
         * Callback from ClockState if something changes, which means we'll need to redraw.
         */
        override fun update(observable: Observable?, data: Any?) {
            invalidate()
        }

        override fun onCreate(holder: SurfaceHolder?) {
            info("onCreate")

            super.onCreate(holder)

            // announce our version number to the logs
            VersionWrapper.logVersion(this@CalWatchFaceService)

            // load any saved preferences
            PreferencesHelper.loadPreferences(this@CalWatchFaceService)

            // TODO: add Wear2 features here
            setWatchFaceStyle(
                    WatchFaceStyle.Builder(this@CalWatchFaceService)
                            .setStatusBarGravity(Gravity.CENTER)
                            .setViewProtectionMode(WatchFaceStyle.PROTECT_WHOLE_SCREEN)
                            .setShowUnreadCountIndicator(true)
                            .setAcceptsTapEvents(true)// we need tap events for permission requests
                            .build())

            BatteryWrapper.init(this@CalWatchFaceService)

            GoogleApiWrapper.startConnection(this@CalWatchFaceService.baseContext, true) {
                verbose { "GoogleApi ready" }
                FitnessWrapper.subscribe()
            }

            val resources = this@CalWatchFaceService.resources

            if (resources == null) {
                error("no resources? not good")
            }

            clockFace = ClockFace()
            clockFace.missingCalendarDrawable = getDrawable(R.drawable.ic_empty_calendar)


            ClockState.addObserver(this) // callbacks if something changes

            CalendarPermission.init(this@CalWatchFaceService)

            initCalendarFetcher()

            ComplicationWrapper.init(this@CalWatchFaceService, this, listOf(BACKGROUND, RIGHT, TOP, BOTTOM), emptyList())
            ComplicationWrapper.styleComplications {
                it.setBackgroundColorActive(PaintCan[PaintCan.STYLE_NORMAL, PaintCan.COLOR_COMPLICATION_BG].color)
                it.setBackgroundColorAmbient(PaintCan[PaintCan.STYLE_AMBIENT, PaintCan.COLOR_COMPLICATION_BG].color)
                it.setBorderColorActive(PaintCan[PaintCan.STYLE_NORMAL, PaintCan.COLOR_COMPLICATION_FG].color)
                it.setBorderColorAmbient(PaintCan[PaintCan.STYLE_AMBIENT, PaintCan.COLOR_COMPLICATION_FG].color)
            }
        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)

            if (properties == null) {
                info("onPropertiesChanged: empty properties?!")
                return
            }

            with (properties) {
                with (clockFace) {
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

            // Useful to dump at the same time.
            FitnessWrapper.report()

            invalidate()
        }

        /*
         * Called when there is updated data for a complication id.
         */
        override fun onComplicationDataUpdate(
                complicationId: Int, complicationData: ComplicationData?) {
            verbose { "onComplicationDataUpdate() id: " + complicationId }

            ComplicationWrapper.updateComplication(complicationId, complicationData)
            invalidate()
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
                oldWidth = width
                oldHeight = height
                clockFace.setSize(width, height)
                ComplicationWrapper.updateBounds(width, height)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            updateBounds(width, height)
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
                        ComplicationWrapper.handleTap(x, y, eventTime)
                }
            }
        }

        override fun onDestroy() {
            verbose("onDestroy")

            GoogleApiWrapper.close()
            ClockState.deleteObserver(this)
            calendarFetcher?.kill()
            clockFace.kill()

            super.onDestroy()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            with(clockFace) {
                round = insets.isRound
                missingBottomPixels = insets.systemWindowInsetBottom
                verbose { "onApplyWindowInsets (round: $round), (chinSize: $missingBottomPixels)" }
            }
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

            // Useful to dump at the same time
            FitnessWrapper.report()
        }
    }

    companion object {
        private var engineRef: WeakReference<Engine>? = null

        val engine: Engine?
            get() = engineRef?.get()
    }
}
