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
    private var visible = false

    override fun onVisibilityChanged(changedView: View?, visibility: Int) {
        visible = visibility == View.VISIBLE

        if (!visible)
            TimeWrapper.frameReport()
        else {
            TimeWrapper.frameReset()
            invalidate()
        }
    }

    /**
     * Callback from ClockState if something changes, which means we'll need
     * to redraw.
     */
    override fun update(observable: Observable?, data: Any?) {
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
            val lClockFace = ClockFace()
            val emptyCalendar = BitmapFactory.decodeResource(context.resources, R.drawable.empty_calendar)
            lClockFace.setMissingCalendarBitmap(emptyCalendar)
            lClockFace.setSize(_width, _height)

            clockFace = lClockFace
        }

        ClockState.addObserver(this)
    }

    // this is redundant with CalWatchFaceService.Engine.initCalendarFetcher, but just different enough that it's
    // not really worth trying to have a grand unified thing.
    fun initCalendarFetcher(activity: Activity) {
        Log.v(TAG, "initCalendarFetcher")

        calendarFetcher?.kill()
        calendarFetcher = null

        val permissionGiven = CalendarPermission.check(activity)

        if (!ClockState.calendarPermission && permissionGiven) {
            // Hypothetically this isn't necessary, because it's handled in CalendarPermission.handleResult.
            // Nonetheless, paranoia.
            Log.e(TAG, "we've got permission, need to update the ClockState")
            ClockState.calendarPermission = true
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

        val lWidth = width // properties of the View, reflected into a Kotlin variable
        val lHeight = height

        if (lWidth == 0 || lHeight == 0) {
            Log.v(TAG, "onWindowFocusChanged: got zeros for width or height")
            return
        }

        this._width = lWidth
        this._height = lHeight

        Log.v(TAG, "onWindowFocusChanged: $_width, $_height")
        clockFace?.setSize(_width, _height)
    }

    fun kill(context: Context) {
        Log.d(TAG, "kill")

        calendarFetcher?.kill()
        calendarFetcher = null

        ClockState.deleteObserver(this)

        clockFace?.kill()
        clockFace = null
    }


    private var drawCounter: Long = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        Log.v(TAG, "onSizeChanged: $w, $h")
        this._width = w
        this._height = h
        clockFace?.setSize(_width, _height)
    }

    // We're using underscores here to make these distinct from "width" and "height" which are
    // properties on the View, which would turn into function calls if we just used them.
    private var _width: Int = 0
    private var _height: Int = 0


    override fun onDraw(canvas: Canvas) {
        drawCounter++

        if (_width == 0 || _height == 0) {
            if (drawCounter % 1000 == 1L)
                Log.e(TAG, "zero-width or zero-height, can't draw yet")
            return
        }

        try {
            val lClockFace = clockFace
            // clear the screen

            if(lClockFace == null) {
                Log.e(TAG, "can't draw when we have no clockface")
                return
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            TimeWrapper.update() // fetch the time
            lClockFace.drawEverything(canvas)

            if (ClockState.subSecondRefreshNeeded(lClockFace))
                invalidate()
        } catch (t: Throwable) {
            if (drawCounter % 1000 == 0L)
                Log.e(TAG, "Something blew up while drawing", t)
        }
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
        private const val TAG = "MyViewAnim"
    }
}

