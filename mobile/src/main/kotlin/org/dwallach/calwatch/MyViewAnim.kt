/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.provider.CalendarContract
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

import java.util.Observable
import java.util.Observer

class MyViewAnim(context: Context, attrs: AttributeSet) : View(context, attrs), Observer {
    init {
        setWillNotDraw(false)
        init(context)
    }

    private var clockFace: ClockFace? = null
    private var clockState: ClockState? = null


    private var visible = false

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        visible = visibility == View.VISIBLE

        if (!visible)
            TimeWrapper.frameReport()
        else {
            TimeWrapper.frameReset()
            invalidate()
        }
    }

    private fun shouldTimerBeRunning(): Boolean {
        var timerNeeded = false

        if (!visible)
            return false

        // run the timer if we're in ambient mode
        if (clockFace != null) {
            timerNeeded = clockFace!!.ambientMode
        }

        // or run the timer if we're not showing the seconds hand
        if (clockState != null) {
            timerNeeded = timerNeeded || !clockState!!.showSeconds
        }

        return timerNeeded
    }

    /**
     * Callback from ClockState if something changes, which means we'll need
     * to redraw.
     */
    override fun update(observable: Observable?, data: Any) {
        invalidate()
    }

    private var calendarFetcher: CalendarFetcher? = null

    fun init(context: Context) {
        Log.d(TAG, "init")

        // announce our version number to the logs
        VersionWrapper.logVersion(context)

        BatteryWrapper.init(context)
        val resources = context.resources

        if (resources == null) {
            Log.e(TAG, "no resources? not good")
        }

        if (clockFace == null) {
            clockFace = ClockFace()
            val emptyCalendar = BitmapFactory.decodeResource(context.resources, R.drawable.empty_calendar)
            clockFace!!.setMissingCalendarBitmap(emptyCalendar)

            clockFace!!.setSize(_width, _height)
        }

        clockState = ClockState.getState()

        clockState!!.deleteObserver(this) // in case we were already there
        clockState!!.addObserver(this) // ensure we're there, but only once

        //        initCalendarFetcher(context);
    }

    // this is redundant with CalWatchFaceService.Engine.initCalendarFetcher, but just different enough that it's
    // not really worth trying to have a grand unified thing.
    fun initCalendarFetcher(activity: Activity) {
        Log.v(TAG, "initCalendarFetcher")
        if (calendarFetcher != null) {
            calendarFetcher!!.kill()
            calendarFetcher = null
        }

        val permissionGiven = CalendarPermission.check(activity)

        if (clockState == null) {
            Log.e(TAG, "null clockState?! Trying again.")
            clockState = ClockState.getState()
        }
        if (!clockState!!.calendarPermission && permissionGiven) {
            // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
            // Nonetheless, paranoia.
            Log.e(TAG, "we've got permission, need to update the clockState")
            clockState!!.calendarPermission = true
        }

        if (!permissionGiven) {
            Log.v(TAG, "no calendar permission given, requesting if we haven't requested beforehand")
            CalendarPermission.requestFirstTimeOnly(activity)
            return
        }

        calendarFetcher = CalendarFetcher(activity, CalendarContract.Instances.CONTENT_URI, CalendarContract.AUTHORITY)
    }


    // when the user started up the config panel, then navigated away and came back, but apparently
    // only under Android 5.0, it would tear down and restart everything, but onSizeChanged() would
    // never actually happen the second time around. This is the workaround.

    // credit where due:
    // http://www.sherif.mobi/2013/01/how-to-get-widthheight-of-view.html
    // http://stackoverflow.com/questions/10411975/how-to-get-the-width-and-height-of-an-image-view-in-android
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        val tmpWidth = width // properties of the View, reflected into a Kotlin variable
        val tmpHeight = height

        if (tmpWidth == 0 || tmpHeight == 0) {
            Log.v(TAG, "onWindowFocusChanged: got zeros for width or height")
            return
        }

        this._width = tmpWidth
        this._height = tmpHeight

        Log.v(TAG, "onWindowFocusChanged: $_width, $_height")
        if (clockFace != null)
            clockFace!!.setSize(_width, _height)
    }

    fun kill(context: Context) {
        Log.d(TAG, "kill")

        if (calendarFetcher != null) {
            calendarFetcher!!.kill()
            calendarFetcher = null
        }

        if (clockState != null) {
            clockState!!.deleteObserver(this)
            clockState = null
        }

        if (clockFace != null) {
            clockFace!!.kill()
            clockFace = null
        }
    }


    private var drawCounter: Long = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Log.v(TAG, "onSizeChanged: $w, $h")
        this._width = w
        this._height = h
        if (clockFace != null)
            clockFace!!.setSize(_width, _height)
    }

    // We're using underscores here to make these distinct from "width" and "height" which are
    // properties on the View, which would turn into function calls if we just used them.
    private var _width: Int = 0
    private var _height: Int = 0


    public override fun onDraw(canvas: Canvas) {
        drawCounter++

        if (_width == 0 || _height == 0) {
            if (drawCounter % 1000 == 1L)
                Log.e(TAG, "zero-width or zero-height, can't draw yet")
            return
        }

        try {
            // clear the screen
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            TimeWrapper.update() // fetch the time
            clockFace!!.drawEverything(canvas)
        } catch (t: Throwable) {
            if (drawCounter % 1000 == 0L)
                Log.e(TAG, "Something blew up while drawing", t)
        }

        // Draw every frame as long as we're visible and doing the sweeping second hand,
        // otherwise the timer will take care of it.
        if (!shouldTimerBeRunning())
            invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP)
            performClick()

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        PhoneActivity.watchfaceClick(this)

        return true
    }

    companion object {
        private val TAG = "MyViewAnim"
    }
}

