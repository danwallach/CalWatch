/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import org.jetbrains.anko.*
import java.util.Observable
import java.util.Observer

/**
 * All of the graphics calls for drawing watchfaces happen here. Note that this class knows
 * nothing about Android widgets, views, or activities. That stuff is handled in MyViewAnim/PhoneActivity
 * (on the phone) and CalWatchFaceService (on the watch).
 */
class ClockFace(val wear: Boolean = false) : Observer, AnkoLogger {
    // an instance of the ClockFace class is created anew alongside the rest of the UI; this number
    // helps us keep track of which instance is which
    private val instanceID: Int

    // initial values to get the ball rolling (avoids a div by zero problem in computeFlatBottomCorners)
    private var cx = DEFAULT_CX
    private var oldCx = -1
    private var cy = DEFAULT_CY
    private var oldCy = -1
    private var radius = DEFAULT_RADIUS

    private var ambientLowBit = FORCE_AMBIENT_LOW_BIT
    private var burnInProtection = FORCE_BURNIN_PROTECTION

    private var missingBottomPixels = 0 // Moto 360 hack; set to non-zero number to pull up the indicia
    private var flatBottomCornerTime = 30f // Moto 360 hack: set to < 30.0 seconds for where the flat bottom starts

    var peekCardRect: Rect? = null // updated by CalWatchFaceService if a peek card shows up

    private var missingCalendarDrawable: Drawable? = null

    private var eventList: List<EventWrapper> = emptyList()
    private var maxLevel: Int = 0

    private var facePathCache: Path? = null
    private var facePathCacheMode = -1

    private var ambientMode = false

    private var drawStyle = PaintCan.STYLE_NORMAL // see setDrawStyle

    // dealing with the "flat tire" a.k.a. "chin" of Moto 360 and any other watches that pull the same trick
    fun setMissingBottomPixels(missingBottomPixels: Int) {
        if (FORCE_MOTO_FLAT_BOTTOM)
            this.missingBottomPixels = 30
        else
            this.missingBottomPixels = missingBottomPixels

        computeFlatBottomCorners()
    }

    /**
     * Tell the clock face if we're in "mute" mode. For now, we don't care.
     */
    fun setMuteMode(muteMode: Boolean) {
        verbose { "setMuteMode: $muteMode" }
    }

    /**
     * If true, ambient redrawing will be purely black and white, without any anti-aliasing (default: off).
     */
    fun setAmbientLowBit(ambientLowBit: Boolean) {
        verbose { "ambient low bit: $ambientLowBit" }
        this.ambientLowBit = ambientLowBit || FORCE_AMBIENT_LOW_BIT

        setDrawStyle()
    }

    private fun setDrawStyle() {
        drawStyle = when {
            ambientMode && ambientLowBit && burnInProtection -> PaintCan.STYLE_ANTI_BURNIN
            ambientMode && ambientLowBit -> PaintCan.STYLE_LOWBIT
            ambientMode -> PaintCan.STYLE_AMBIENT
            else -> PaintCan.STYLE_NORMAL
        }
    }

    init {
        instanceID = instanceCounter++
        verbose { "ClockFace setup, instance($instanceID)" }

        ClockState.addObserver(this) // so we get callbacks when the clock state changes
        update(null, null) // and we'll do that callback for the first time, just to initialize things
        setMissingBottomPixels(0) // just to get things started; flat bottom detection happens later
    }

    /**
     * Call this at initialization time to set up the icon for the missing calendar.
     */
    fun setMissingCalendarDrawable(drawable: Drawable) {
        missingCalendarDrawable = drawable

        updateMissingCalendarRect()
    }


    private fun updateMissingCalendarRect() {
        val lMissingCalendarDrawable = missingCalendarDrawable

        if(lMissingCalendarDrawable == null) {
            error("no missing calendar drawable?!")
            return
        }

        val height = lMissingCalendarDrawable.intrinsicHeight
        val width = lMissingCalendarDrawable.intrinsicWidth

        val x = clockX(15.0, 0.2f).toInt()
        val y = (clockY(0.0, 0f) - height / 2).toInt()

        // setBounds arguments: (int left, int top, int right, int bottom)
        lMissingCalendarDrawable.setBounds(x, y, x+width, y+height)

        verbose { "missing calendar drawable size: (%d,%d), coordinates: (%d,%d),  (cy: %d, radius: %d)"
                .format(width, height, x, y, cy, radius) }
    }

    /**
     * Call this from your onDraw() method. Note that one possible side-effect of this will
     * be the asynchronous refresh of the calendar database (inside CalendarFetcher), which might
     * not complete for seconds after drawEverything() returns.
     */
    fun drawEverything(canvas: Canvas) {
        TimeWrapper.frameStart()


        try {
            // This will start an asynchronous query to update the calendar state; we're doing this on
            // every screen refresh because it's dirt cheap and we want to be current. (This call eventually
            // dives down into CalendarFetcher where it will check if the hour has changed since the last
            // refresh. If not, it becomes a no-op.)
            updateEventList()

            // the calendar goes on the very bottom and everything else stacks above; the code for calendar drawing
            // works great in low-bit mode, leaving big white wedges, but this violates the rules, per Google:
            // "For OLED screens, you must use a black background. Non-background pixels must be less than 10
            // percent of total pixels. You can use low-bit color for up to 5 percent of pixels on screens that support it."
            // -- http://developer.android.com/design/wear/watchfaces.html
            if (drawStyle == PaintCan.STYLE_NORMAL || drawStyle == PaintCan.STYLE_AMBIENT) drawCalendar(canvas)

            // next, we draw the indices or numbers of the watchface
            drawFace(canvas)

            // Next up, the step counter and battery meter.
            //
            // We disable the battery meter when we're in ambientMode with burnInProtection.
            // The step counter knows how to draw itself differently in different modes.
            if (drawStyle != PaintCan.STYLE_ANTI_BURNIN) drawBattery(canvas)
            drawStepCount(canvas)

            // Kludge for peek card until we come up with something better:
            // if there's a peek card *and* we're in ambient mode, *then* draw
            // a solid black box behind the peek card, which would otherwise be transparent.
            // Note that we're doing this *before* drawing the hands but *after* drawing
            // everything else. I want the hands to not be chopped off, even though everything
            // else will be.
            if (peekCardRect != null && drawStyle != PaintCan.STYLE_NORMAL)
                canvas.drawRect(peekCardRect, PaintCan[drawStyle, PaintCan.COLOR_BLACK_FILL])

            drawHands(canvas)

            // something a real watch can't do: float the text over the hands
            // (but disable when we're in ambientMode with burnInProtection)
            if (drawStyle != PaintCan.STYLE_ANTI_BURNIN && ClockState.showDayDate) drawMonthBox(canvas)
        } catch (th: Throwable) {
            error("exception in drawEverything", th)
        } finally {
            TimeWrapper.frameEnd()
        }
    }

    private fun drawRadialLine(canvas: Canvas, seconds: Double, startRadius: Float, endRadius: Float, paint: Paint, shadowPaint: Paint?, forceVertical: Boolean = false) {
        val p = Path()
        drawRadialLine(p, paint.strokeWidth, seconds, startRadius, endRadius, forceVertical, false)
        canvas.drawPath(p, paint)
        if (shadowPaint != null)
            canvas.drawPath(p, shadowPaint)
    }

    private fun drawRadialLine(path: Path, startStrokeWidth: Float, seconds: Double, startRadiusRatio: Float, endRadiusRatio: Float, forceVertical: Boolean, flatBottomHack: Boolean) {
        var lseconds = seconds
        var startRadius = startRadiusRatio
        var endRadius = endRadiusRatio
        var strokeWidth = startStrokeWidth

        if (flatBottomHack) {
            val clipRadius = radiusToEdge(lseconds)
            if (endRadius > clipRadius) {
                val dr = endRadius - clipRadius
                startRadius -= dr
                endRadius -= dr
            }
        }

        if (drawStyle == PaintCan.STYLE_ANTI_BURNIN) {
            // scale down everything to leave a 10 pixel margin

            val ratio = (radius - 10f) / radius
            startRadius *= ratio
            endRadius *= ratio
            strokeWidth *= ratio
        }

        val x1 = clockX(lseconds, startRadius)
        val y1 = clockY(lseconds, startRadius)
        var x2 = clockX(lseconds, endRadius)
        val y2 = clockY(lseconds, endRadius)
        if (forceVertical) {
            lseconds = 0.0
            x2 = x1
        }

        val dx = (clockX(lseconds + 15, 1f) - cx) * 0.5f * strokeWidth / radius
        val dy = (clockY(lseconds + 15, 1f) - cy) * 0.5f * strokeWidth / radius

        path.moveTo(x1 + dx, y1 + dy)
        path.lineTo(x2 + dx, y2 + dy)
        path.lineTo(x2 - dx, y2 - dy)
        path.lineTo(x1 - dx, y1 - dy)
        // path.lineTo(x1+dx, y1+dy)
        path.close()
    }

    private fun getRectRadius(radius: Float): RectF {
        return RectF(
                clockX(45.0, radius), // left
                clockY(0.0, radius), // top
                clockX(15.0, radius), // right
                clockY(30.0, radius))// bottom
    }

    /**
     * Draws a radial arc on the canvas with all the specific start and end times, paint, etc.
     * The path used for drawing is returned and may be passed back in next time, to the *path*
     * argument, which will make things go much faster.
     */
    private fun drawRadialArc(canvas: Canvas, path: Path?, secondsStart: Double, secondsEnd: Double, startRadiusRatio: Float, endRadiusRatio: Float, paint: Paint, outlinePaint: Paint?, lowBitSquish: Boolean = true): Path {
        var startRadius = startRadiusRatio
        var endRadius = endRadiusRatio
        /*
         * Below is an attempt to do this "correctly" using the arc functionality supported natively
         * by Android's Path.
         */

        if (startRadius < 0 || startRadius > 1 || endRadius < 0 || endRadius > 1) {
            // if this happens, then we've got a serious bug somewhere; time for a kaboom
            errorLogAndThrow("arc too big! radius(%.2f -> %.2f), seconds(%.2f -> %.2f)".format(startRadius, endRadius, secondsStart, secondsEnd))
        }

        if (drawStyle == PaintCan.STYLE_ANTI_BURNIN) {
            // scale down everything to leave a 10 pixel margin

            val ratio = (radius - 10f) / radius

            startRadius *= ratio
            endRadius *= ratio
        }

        var p: Path? = path

        if (p == null) {
            p = Path()

            val midOval = getRectRadius((startRadius + endRadius) / 2f + 0.025f)
            val midOvalDelta = getRectRadius((startRadius + endRadius) / 2f - 0.025f)
            val startOval = getRectRadius(startRadius)
            val endOval = getRectRadius(endRadius)
            if (drawStyle == PaintCan.STYLE_ANTI_BURNIN && lowBitSquish) {
                // In ambient low-bit mode, we originally drew some slender arcs of fixed width at roughly the center of the big
                // colored pie wedge which we normally show when we're not in ambient mode. Currently this is dead code because
                // drawCalendar() becomes a no-op in ambientLowBit when burn-in protection is on.
                p.arcTo(midOval, (secondsStart * 6 - 90).toFloat(), ((secondsEnd - secondsStart) * 6).toFloat(), true)
                p.arcTo(midOvalDelta, (secondsEnd * 6 - 90).toFloat(), (-(secondsEnd - secondsStart) * 6).toFloat())
                p.close()
            } else {
                p.arcTo(startOval, (secondsStart * 6 - 90).toFloat(), ((secondsEnd - secondsStart) * 6).toFloat(), true)
                p.arcTo(endOval, (secondsEnd * 6 - 90).toFloat(), (-(secondsEnd - secondsStart) * 6).toFloat())
                p.close()

                //            Log.e(TAG, "New arc: radius(" + Float.toString((float) startRadius) + "," + Float.toString((float) endRadius) +
                //                    "), seconds(" + Float.toString((float) secondsStart) + "," + Float.toString((float) secondsEnd) + ")")
                //            Log.e(TAG, "--> arcTo: startOval, " + Float.toString((float) (secondsStart * 6 - 90)) + ", " +  Float.toString((float) ((secondsEnd - secondsStart) * 6)))
                //            Log.e(TAG, "--> arcTo: endOval, " + Float.toString((float) (secondsEnd * 6 - 90)) + ", " +  Float.toString((float) (-(secondsEnd - secondsStart) * 6)))
            }
        }

        canvas.drawPath(p, paint)
        if(outlinePaint != null)
            canvas.drawPath(p, outlinePaint)

        return p
    }

    private fun drawMonthBox(canvas: Canvas) {
        // for now, hard-coded to the 9-oclock position
        val m = TimeWrapper.localMonthDay()
        val d = TimeWrapper.localDayOfWeek()
        val x1 = clockX(45.0, .85f)
        val y1 = clockY(45.0, .85f)

        val paint = PaintCan[drawStyle, PaintCan.COLOR_SMALL_TEXT_AND_LINES]
        val shadow = PaintCan[drawStyle, PaintCan.COLOR_SMALL_SHADOW]

        // AA note: we only draw the month box when in normal mode, not ambient, so no AA gymnastics here

        val metrics = paint.fontMetrics
        val dybottom = -metrics.ascent - metrics.leading // smidge it up a bunch
        val dytop = -metrics.descent // smidge it down a little

        drawShadowText(canvas, d, x1, y1 + dybottom, paint, shadow)
        drawShadowText(canvas, m, x1, y1 + dytop, paint, shadow)
    }

    private fun drawShadowText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, shadowPaint: Paint?) {
        if(shadowPaint != null)
            canvas.drawText(text, x, y, shadowPaint)
        canvas.drawText(text, x, y, paint)
    }

    private fun drawFace(canvas: Canvas) {
        var lFacePathCache = facePathCache

        val bottomHack = missingBottomPixels > 0

        // force "lite" mode when in burn-in protection mode
        val lFaceMode = if(drawStyle == PaintCan.STYLE_ANTI_BURNIN) ClockState.FACE_LITE else ClockState.faceMode

        val colorTickShadow = PaintCan[drawStyle, PaintCan.COLOR_TICK_SHADOW]
        val colorSmall = PaintCan[drawStyle, PaintCan.COLOR_SMALL_TEXT_AND_LINES]
        val colorBig = PaintCan[drawStyle, PaintCan.COLOR_BIG_TEXT_AND_LINES]
        val colorTextShadow = PaintCan[drawStyle, PaintCan.COLOR_BIG_SHADOW]

        // check if we've already rendered the face
        if (lFaceMode != facePathCacheMode || lFacePathCache == null) {
            verbose { "drawFace: cx($cx), cy($cy), r($radius)" }

            lFacePathCache = Path()

            verbose { "rendering new face, faceMode($lFaceMode)" }

            if (lFaceMode == ClockState.FACE_TOOL)
                for (i in 1..59)
                    if (i % 5 != 0)
                        drawRadialLine(lFacePathCache, colorSmall.strokeWidth, i.toDouble(), .9f, 1.0f, false, bottomHack)

            val strokeWidth =
                    if (lFaceMode == ClockState.FACE_LITE || lFaceMode == ClockState.FACE_NUMBERS)
                        colorSmall.strokeWidth
                    else
                        colorBig.strokeWidth


            for(i in 0..59 step 5) {
                if (i == 0) {
                    // top of watch: special
                    if (lFaceMode != ClockState.FACE_NUMBERS) {
                        drawRadialLine(lFacePathCache, strokeWidth, -0.4, .75f, 1.0f, true, false)
                        drawRadialLine(lFacePathCache, strokeWidth, 0.4, .75f, 1.0f, true, false)
                    }
                } else if (i == 45 && drawStyle != PaintCan.STYLE_ANTI_BURNIN && ClockState.showDayDate) {
                    // 9 o'clock, don't extend into the inside, where we'd overlap with the DayDate display
                    // except, of course, when we're not showing the DayDate display, which might happen
                    // via user preference or because we're in burnInProtection ambient mode
                    drawRadialLine(lFacePathCache, strokeWidth, i.toDouble(), 0.9f, 1.0f, false, false)
                } else {
                    // we want lines for 1, 2, 4, 5, 7, 8, 10, and 11 no matter what
                    if (lFaceMode != ClockState.FACE_NUMBERS || !(i == 15 || i == 30 || i == 45)) {
                        // in the particular case of 6 o'clock and the Moto 360 bottomHack, we're
                        // going to make the 6 o'clock index line the same length as the other lines
                        // so it doesn't stand out as much
                        if (i == 30 && bottomHack)
                            drawRadialLine(lFacePathCache, strokeWidth, i.toDouble(), .9f, 1.0f, false, bottomHack)
                        else
                            drawRadialLine(lFacePathCache, strokeWidth, i.toDouble(), .75f, 1.0f, false, bottomHack)
                    }
                }
            }

            facePathCache = lFacePathCache
            facePathCacheMode = lFaceMode
        }

        canvas.drawPath(lFacePathCache, colorSmall)
        canvas.drawPath(lFacePathCache, colorTickShadow)

        if (lFaceMode == ClockState.FACE_NUMBERS) {
            // in this case, we'll draw "12", "3", and "6". No "9" because that's where the
            // month and day will go
            var x: Float
            var y: Float
            var r: Float

            //
            // note: metrics.ascent is a *negative* number while metrics.descent is a *positive* number
            //
            val metrics = colorBig.fontMetrics


            //
            // 12 o'clock
            //
            r = 0.9f

            x = clockX(0.0, r)
            y = clockY(0.0, r) - metrics.ascent / 1.5f

            drawShadowText(canvas, "12", x, y, colorBig, colorTextShadow)

            if (!debugMetricsPrinted) {
                debugMetricsPrinted = true
                verbose { "x(%.2f), y(%.2f), metrics.descent(%.2f), metrics.asacent(%.2f)".format(x, y, metrics.descent, metrics.ascent) }
            }

            //
            // 3 o'clock
            //

            r = 0.9f
            val threeWidth = colorBig.measureText("3")

            x = clockX(15.0, r) - threeWidth / 2f
            y = clockY(15.0, r) - metrics.ascent / 2f - metrics.descent / 2f // empirically gets the middle of the "3" -- actually a smidge off with Roboto but close enough for now and totally font-dependent with no help from metrics

            drawShadowText(canvas, "3", x, y, colorBig, colorTextShadow)

            //
            // 6 o'clock
            //

            r = 0.9f

            x = clockX(30.0, r)
            if (missingBottomPixels != 0)
                y = clockY(30.0, r) + metrics.descent - missingBottomPixels // another hack for Moto 360
            else
                y = clockY(30.0, r) + 0.75f * metrics.descent // scoot it up a tiny bit

            drawShadowText(canvas, "6", x, y, colorBig, colorTextShadow)

            //
            // 9 o'clock
            //

            if (!ClockState.showDayDate) {
                r = 0.9f
                val nineWidth = colorBig.measureText("9")

                x = clockX(45.0, r) + nineWidth / 2f
                y = clockY(45.0, r) - metrics.ascent / 2f - metrics.descent / 2f

                drawShadowText(canvas, "9", x, y, colorBig, colorTextShadow)


            }
        }
    }

    private fun drawHands(canvas: Canvas) {
        val time = TimeWrapper.localTime

        val seconds = time / (1.seconds.toDouble())
        val minutes = seconds / 60.0
        val hours = minutes / 12.0  // because drawRadialLine is scaled to a 60-unit circle


        val shadowColor = PaintCan[drawStyle, PaintCan.COLOR_HAND_SHADOW]
        val hourColor = PaintCan[drawStyle, PaintCan.COLOR_HOUR_HAND]
        val minuteColor = PaintCan[drawStyle, PaintCan.COLOR_MINUTE_HAND]

        drawRadialLine(canvas, hours, 0.1f, 0.6f, hourColor, shadowColor)
        drawRadialLine(canvas, minutes, 0.1f, 0.9f, minuteColor, shadowColor)

        if (drawStyle == PaintCan.STYLE_NORMAL && ClockState.showSeconds) {
            val secondsColor = PaintCan[PaintCan.STYLE_NORMAL, PaintCan.COLOR_SECOND_HAND]
            // ugly details: we might run 10% or more away from our targets at 4Hz, making the second
            // hand miss the indices. Ugly. Thus, some hackery.
            drawRadialLine(canvas, nonLinearSeconds(seconds), 0.1f, 0.95f, secondsColor, shadowColor)
        }
    }

    /**
     * call this if external forces at play may have invalidated state
     * being saved inside ClockFace
     */
    fun wipeCaches() {
        verbose { "clearing caches" }

        facePathCache = null
        batteryPathCache = null
        batteryCritical = false
        stipplePathCache = null
        stippleTimeCache = -1

        eventList.forEach { it.path = null }
    }

    private var stippleTimeCache: Long = -1
    private var stipplePathCache: Path? = null

    private fun drawCalendar(canvas: Canvas) {
        calendarTicker++

        // if we don't have permission to see the calendar, then we'll let the user know, but we won't
        // bug them in any of the ambient modes.
        if (!ClockState.calendarPermission && drawStyle == PaintCan.STYLE_NORMAL) {
            missingCalendarDrawable?.draw(canvas)
            return
        }

        val time = TimeWrapper.localTime

        eventList.forEach {
            val e = it.wireEvent
            val evMinLevel = it.minLevel
            val evMaxLevel = it.maxLevel

            val startTime = e.startTime
            val endTime = e.endTime

            //
            // Our drawing routines take angles that go from 0-60. By dividing by 12 minutes,
            // that ensures that 12 hours will go all the way around. Why? Because:
            //
            // 60 * 12 minutes = 12 hours
            //
            val twelveMinutes = 12.minutes.toDouble()

            val arcStart: Double = startTime / twelveMinutes
            val arcEnd: Double = endTime / twelveMinutes

            // path caching happens inside drawRadialArc

            val arcColor = it.getPaint(drawStyle)
            val arcShadow = PaintCan[drawStyle, PaintCan.COLOR_ARC_SHADOW]

            it.path = drawRadialArc(canvas, it.path, arcStart, arcEnd,
                    CALENDAR_RING_MAX_RADIUS - evMinLevel * CALENDAR_RING_WIDTH / (maxLevel + 1),
                    CALENDAR_RING_MAX_RADIUS - (evMaxLevel + 1) * CALENDAR_RING_WIDTH / (maxLevel + 1),
                    arcColor, arcShadow)
        }

        // Lastly, draw a stippled pattern at the current hour mark to delineate where the
        // twelve-hour calendar rendering zone starts and ends.


        // integer division gets us the exact hour, then multiply by 5 to scale to our
        // 60-second circle
        var stippleTime = time / 1.hours
        stippleTime *= 5

        // we might want to rejigger this to be paranoid about concurrency smashing stipplePathCache,
        // but it's less of a problem here than with the watchFace, because the external UI isn't
        // inducing the state here to change
        if (stippleTime != stippleTimeCache || stipplePathCache == null) {
            val lStipplePathCache = Path()
            stippleTimeCache = stippleTime

            //            if(calendarTicker % 1000 == 0)
            //                Log.v(TAG, "StippleTime(" + stippleTime +
            //                        "),  currentTime(" + Float.toString((time) / 720000f) + ")")

            var r1: Float
            var r2: Float

            // eight little diamonds -- precompute the deltas when we're all the way out at the end,
            // then apply elsewhere

            val stippleWidth = 0.3f
            val stippleSteps = 8
            val rDelta = CALENDAR_RING_WIDTH / stippleSteps.toFloat()

            var x1: Float = clockX(stippleTime.toDouble(), CALENDAR_RING_MAX_RADIUS)
            var y1: Float = clockY(stippleTime.toDouble(), CALENDAR_RING_MAX_RADIUS)
            var x2: Float = clockX(stippleTime.toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta)
            var y2: Float = clockY(stippleTime.toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta)
            var xmid: Float = (x1 + x2) / 2f
            var ymid: Float = (y1 + y2) / 2f
            var xlow: Float = clockX((stippleTime - stippleWidth).toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta / 2)
            var ylow: Float = clockY((stippleTime - stippleWidth).toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta / 2)
            var xhigh: Float = clockX((stippleTime + stippleWidth).toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta / 2)
            var yhigh: Float = clockY((stippleTime + stippleWidth).toDouble(), CALENDAR_RING_MAX_RADIUS - rDelta / 2)
            val dxlow: Float = xmid - xlow
            val dylow: Float = ymid - ylow
            val dxhigh: Float = xmid - xhigh
            val dyhigh: Float = ymid - yhigh

            r1 = CALENDAR_RING_MIN_RADIUS
            x1 = clockX(stippleTime.toDouble(), r1)
            y1 = clockY(stippleTime.toDouble(), r1)

            for(i in 0..7) {
                r2 = r1 + CALENDAR_RING_WIDTH / 8f
                x2 = clockX(stippleTime.toDouble(), r2)
                y2 = clockY(stippleTime.toDouble(), r2)

                xmid = (x1 + x2) / 2f
                ymid = (y1 + y2) / 2f

                xlow = xmid - dxlow
                ylow = ymid - dylow
                xhigh = xmid - dxhigh
                yhigh = ymid - dyhigh

                // Path p = new Path()
                lStipplePathCache.moveTo(x1, y1)
                lStipplePathCache.lineTo(xlow, ylow)
                lStipplePathCache.lineTo(x2, y2)
                lStipplePathCache.lineTo(xhigh, yhigh)
                lStipplePathCache.close()
                r1 = r2
                x1 = x2
                y1 = y2
                // canvas.drawPath(p, black)

                //                if(calendarTicker % 1000 == 0)
                //                    Log.v(TAG, "x1(" + Float.toString(x1) + "), y1(" + Float.toString(y1) +
                //                            "), x2(" + Float.toString(x1) + "), y2(" + Float.toString(y2) +
                //                            "), xlow(" + Float.toString(xlow) + "), ylow(" + Float.toString(ylow) +
                //                            "), xhigh(" + Float.toString(xhigh) + "), yhigh(" + Float.toString(yhigh) +
                //                            ")")
            }

            stipplePathCache = lStipplePathCache
        }
        canvas.drawPath(stipplePathCache, PaintCan[drawStyle, PaintCan.COLOR_BLACK_FILL])
    }

    private var batteryPathCache: Path? = null
    private var batteryCritical = false
    private var batteryCacheTime: Long = 0

    private fun drawBattery(canvas: Canvas) {
        val time = TimeWrapper.gmtTime

        // we don't want to poll *too* often; the code below translates to about once per five minute
        if (batteryPathCache == null || time - batteryCacheTime > 5.minutes) {
            verbose("fetching new battery status")
            BatteryWrapper.fetchStatus()
            val batteryPct: Float = BatteryWrapper.batteryPct
            batteryCacheTime = time
            val lBatteryPathCache = Path()

            //
            // The concept: draw nothing unless the battery is low. At 50%, we start a small yellow
            // circle. This scales in radius until it hits max size at 10%, then it switches to red.
            //

            verbose { "battery at $batteryPct" }
            if (batteryPct > Constants.POWER_WARN_LOW_LEVEL) {
                // batteryPathCache = null
            } else {
                val minRadius = 0.01f
                val maxRadius = 0.06f
                batteryCritical = batteryPct <= Constants.POWER_WARN_CRITICAL_LEVEL

                val dotRadius = maxRadius - if (batteryCritical) 0f else
                    (maxRadius - minRadius) * (batteryPct - Constants.POWER_WARN_CRITICAL_LEVEL) / (Constants.POWER_WARN_LOW_LEVEL - Constants.POWER_WARN_CRITICAL_LEVEL)

                lBatteryPathCache.addCircle(cx.toFloat(), cy.toFloat(), radius * dotRadius, Path.Direction.CCW) // direction shouldn't matter

                verbose { "--> dot radius: $dotRadius, critical: $batteryCritical" }
            }

            batteryPathCache = lBatteryPathCache
        }

        // note that we'll flip the color from white to red once the battery gets below 10%
        // (in ambient mode, we can't show it at all because of burn-in issues)
        if (batteryPathCache != null) {
            val paint = PaintCan[drawStyle, if (batteryCritical) PaintCan.COLOR_BATTERY_CRITICAL else PaintCan.COLOR_BATTERY_LOW]
            canvas.drawPath(batteryPathCache, paint)
        }
    }

    private var stepCountPath: Path? = null
    private var oldStepCount = 0

    private fun drawStepCount(canvas: Canvas) {
        // There's a battery strategy going on in here that's a bit complex. We have three battery
        // ranges to worry about: full, warning, and critical (defined in Constants). When we're
        // full (or at least, above the warning level), then we'll draw everything. Once we hit
        // the warning level, we'll kill off the digits because drawBattery() will start rendering
        // its power warning data. Once we hit the critical level, we're going to kill off the
        // step counter in its entirety because the user really needs to get their phone back
        // on the charger, and because the step counter seems to have a non-zero impact on battery
        // life, so we'll try to save a bit on power usage.

        if(!ClockState.showStepCounter || BatteryWrapper.batteryPct < Constants.POWER_WARN_CRITICAL_LEVEL) return

        val rawStepCount = FitnessWrapper.getStepCount()

        // special case if we're on mobile (setup UI), so the user sees *something*
        val stepCount = if(rawStepCount == 0 && !wear) 6000 else rawStepCount

        if(stepCount == 0) return // nothing to do!

        if(oldStepCount != stepCount) {
            oldStepCount = stepCount
            stepCountPath = null // force the path to be recomputed
        }

        // We're going to draw an arc from 12 o'clock, going clockwise, where an arc, all the way
        // around represents 12000 steps. That's an arbitrary constant, but it fits nicely with
        // the 12 hours in the dial. We'll also be showing a digit (code below) which will help
        // train the user.

        val paint = PaintCan[drawStyle, PaintCan.COLOR_STEP_COUNT]
        val outlinePaint = PaintCan[drawStyle, PaintCan.COLOR_STEP_COUNT]

        val seconds: Double = if(stepCount > 12000) { 60.0 } else { stepCount / 200.0 }

        // the battery ends at radius 0.06f and the hands start at 0.1f; we're going to draw this arc
        // in between

        stepCountPath = drawRadialArc(canvas, stepCountPath, 0.0, seconds, 0.065f, 0.090f, paint, outlinePaint, false)

        // next, we're going to put the thousands-digit of the step counter in the dead center, but
        // only when we're in "normal" mode (i.e., we're not in one of the ambient modes).

        if(drawStyle == PaintCan.STYLE_NORMAL && BatteryWrapper.batteryPct > Constants.POWER_WARN_LOW_LEVEL) {
            // we're rounding to the nearest thousand, i.e.,  1499 -> 1, 1500 -> 2, etc.
            val stepCountString =
                    if (rawStepCount == 0 && !wear)
                        "?" // make it clear that we're faking it on mobile
                    else {
                        val stepCountDigits = Math.floor((stepCount + 500.0) / 1000.0)
                        if (stepCountDigits > 99) "++" else stepCountDigits.toInt().toString()
                    }

            val textPaint = PaintCan[drawStyle, PaintCan.COLOR_STEP_COUNT_TEXT]

            // AA note: we only draw the month box when in normal mode, not ambient, so no AA gymnastics here

            val metrics = textPaint.fontMetrics
            // note: metrics.ascent is a *negative* number while metrics.descent is a *positive* number;
            // we're trying to vertically center the text around the watch origin, so we need a correction here
            val dy = (-metrics.ascent - metrics.descent)/2f

            drawShadowText(canvas, stepCountString, cx.toFloat(), cy.toFloat() + dy, textPaint, null)
        }
    }

    fun setSize(width: Int, height: Int) {
        verbose { "setSize: $width x $height" }
        cx = width / 2
        cy = height / 2

        if (cx == oldCx && cy == oldCy) return  // nothing changed, we're done

        oldCx = cx
        oldCy = cy

        radius = if (cx > cy) cy else cx // minimum of the two

        // This creates all the Paint objects used throughout the draw routines
        // here. Everything scales with the radius of the watchface, which is why
        // we're calling it from here.
        PaintCan.initPaintBucket(radius.toFloat())

        computeFlatBottomCorners()

        updateMissingCalendarRect()

        wipeCaches()
    }

    // coordinates of each corner where the flat tire begins.
    // (x1, y1) is before 6 o'clock
    // (x2, y2) is after 6 o'clock
    // _R100 is at a radius of 1
    // _R80 is at a radius of 0.80, giving us something of a beveled edge
    // (we'll be bilinearly interpolating between these two points, to determine a location on the flat bottom
    //  for any given time and radius -- see flatBottomX() and flatBottomY())

    private var flatBottomCornerY1_R100: Float = 0f
    private var flatBottomCornerY1_R80: Float = 0f

    private fun computeFlatBottomCorners() {
        // What angle does the flat bottom begin and end?
        //
        // We want to solve for clockY(seconds, 1.0) = height - missingBottomPixels
        //
        // cy + radius * sin(angle) = height - missingBottomPixels
        // if (width == height)
        //    radius = cy = height / 2
        //    cy + cy * sin(angle) = 2 * cy - missingBottomPixels
        //    cy * sin(angle) = cy - missingBottomPixels
        //    sin(angle) = 1 - missingBottomPixels / cy
        //    angle = arcsin(1 - missingBottomPixels / cy)
        //    ((seconds - 15) * PI * 2) / 60 = arcsin(1 - missing / cy)
        //    seconds = arcsin(...) * 60 / (2*PI) + 15
        //    seconds = arcsin(...) * 30 / PI + 15
        // else
        //    it's a non-rectangular screen, and (to date anyway) no flat tire to worry about

        if (missingBottomPixels != 0) {
            val angle = Math.asin(1.0 - missingBottomPixels.toDouble() / cy.toDouble())

            flatBottomCornerTime = (angle * 30.0 / Math.PI + 15).toFloat()
            verbose { "flatBottomCornerTime($flatBottomCornerTime) <-- angle($angle), missingBottomPixels($missingBottomPixels), cy($cy)" }

            flatBottomCornerY1_R100 = clockY(flatBottomCornerTime.toDouble(), 1f)
            flatBottomCornerY1_R80 = clockY(flatBottomCornerTime.toDouble(), 0.80f)
        } else {
            verbose { "no flat bottom corrections" }
        }
    }

    private fun interpolate(a1: Float, t1: Float, a2: Float, t2: Float, t: Float): Float {
        // given two values a1 and a2 associated with time parameters t1 and t2, find the
        // interpolated value for a given the time t
        //
        // example: a1 = 4, a2 = 10, t1=10, t2=30
        // interpolateT: t -> a
        //    10 -> 4
        //    20 -> 7
        //    30 -> 10
        //    50 -> 16  (we keep extrapolating on either end)
        val da = a2 - a1
        val dt = t2 - t1
        val ratio = (t - t1) / dt
        return a1 + da * ratio
    }

    private fun flatBottomX(time: Float, radius: Float): Float {
        // old, incorrect approach: linear interpolation between the sides
        //        float x1 = interpolate(flatBottomCornerX1_R80, 0.8f, flatBottomCornerX1_R100, 1f, radius)
        //        float x2 = interpolate(flatBottomCornerX2_R80, 0.8f, flatBottomCornerX2_R100, 1f, radius)
        //        return interpolate(x1, flatBottomCornerTime, x2, 60 - flatBottomCornerTime, time)

        // new shiny approach: project a line from the center, intersect it with the flat-bottom line

        val finalY = flatBottomY(time, radius)
        val r1X = clockX(time.toDouble(), 1f)
        val r1Y = clockY(time.toDouble(), 1f)

        return interpolate(r1X, r1Y, cx.toFloat(), cy.toFloat(), finalY)
    }

    private fun flatBottomY(time: Float, radius: Float): Float {
        // old, incorrect approach: linear interpolation between the sides
        //        float y1 = interpolate(flatBottomCornerY1_R80, 0.8f, flatBottomCornerY1_R100, 1f, radius)
        //        float y2 = interpolate(flatBottomCornerY2_R80, 0.8f, flatBottomCornerY2_R100, 1f, radius)
        //        return interpolate(y1, flatBottomCornerTime, y2, 60 - flatBottomCornerTime, time)

        // new shiny approach: we're just returning the magic Y-value as above

        return interpolate(flatBottomCornerY1_R80, 0.8f, flatBottomCornerY1_R100, 1f, radius)
    }


    // clock math
    private fun clockX(seconds: Double, fractionFromCenter: Float): Float {
        val angleRadians = (seconds - 15) * Math.PI * 2.0 / 60.0
        return (cx + radius.toDouble() * fractionFromCenter.toDouble() * Math.cos(angleRadians)).toFloat()
    }

    private fun clockY(seconds: Double, fractionFromCenter: Float): Float {
        val angleRadians = (seconds - 15) * Math.PI * 2.0 / 60.0
        return (cy + radius.toDouble() * fractionFromCenter.toDouble() * Math.sin(angleRadians)).toFloat()
    }

    // hack for Moto360: given the location on the dial (seconds), and the originally
    // desired radius, this returns your new radius that will touch the flat bottom
    private fun radiusToEdge(seconds: Double): Float {
        val yOrig = clockY(seconds, 1f)
        if (yOrig > cy * 2 - missingBottomPixels) {
            // given:
            //   yOrig = cy + radius * fractionFromCenter * sin(angle)
            // substitute the desired Y, i.e.,
            //   cy*2 - missingBottomPixels = cy + radius * fractionFromCenter * sin(angle)
            // and now solve for fractionFromCenter:
            //   (cy - missingBottomPixels) / (radius * sin(angle)) = fractionFromCenter
            val angleRadians = (seconds - 15) * Math.PI * 2.0 / 60.0
            try {
                return ((cy - missingBottomPixels) / (radius * Math.sin(angleRadians))).toFloat()
            } catch (e: ArithmeticException) {
                // division by zero, weird, so fall back to the default
                return 1f
            }

        } else
            return 1f
    }

    fun getAmbientMode() = ambientMode
    fun setAmbientMode(ambientMode: Boolean): Unit {
        info { "Ambient mode: ${this.ambientMode} -> $ambientMode" }
        if (ambientMode == this.ambientMode) return // nothing changed, so we're good
        this.ambientMode = ambientMode

        setDrawStyle()
        wipeCaches()
    }

    // call this if you want this instance to head to the garbage collector; this disconnects
    // it from paying attention to changes in the ClockState
    fun kill() {
        ClockState.deleteObserver(this)
    }

    // this gets called when the ClockState updates itself
    override fun update(observable: Observable?, data: Any?) {
        verbose { "update - instance($instanceID)" }
        wipeCaches() // nuke saved Paths and such, because all sorts of state may have just changed
        verbose { "caches wiped ($instanceID)" }
    }

    private fun updateEventList() {
        // This is cheap enough that we can afford to do it at 60Hz, although the call to getVisibleEventList()
        // might start a task, on a different thread, to update the events from the calendar. If that happens,
        // ClockState will be updated asynchronously, and the next time we come here, we'll get the latest
        // events. While that background task is running, this call will give us the original events
        // every time.
        this.maxLevel = ClockState.maxLevel
        this.eventList = ClockState.getVisibleEventList()
    }

    fun setBurnInProtection(burnInProtection: Boolean) {
        this.burnInProtection = burnInProtection || FORCE_BURNIN_PROTECTION

        setDrawStyle()
    }

    /**
     * Let us know if we're on a square or round watchface. (We don't really care. For now.)
     */
    fun setRound(round: Boolean) {
        verbose { "setRound: $round" }
    }

    companion object {
        // for testing purposes, turn these things on; disable for production
        private const val FORCE_AMBIENT_LOW_BIT = false
        private const val FORCE_BURNIN_PROTECTION = false
        private const val FORCE_MOTO_FLAT_BOTTOM = false

        // for testing: sometimes it seems we have multiple instances of ClockFace, which is bad; let's
        // try to track them
        private var instanceCounter = 0


        // Android Wear eventually tells us these numbers, but best to start off with something in
        // the meanwhile.
        private const val DEFAULT_CX = 140
        private const val DEFAULT_CY = 140
        private const val DEFAULT_RADIUS = 140

        private const val CALENDAR_RING_MIN_RADIUS = 0.2f
        private const val CALENDAR_RING_MAX_RADIUS = 0.9f
        private const val CALENDAR_RING_WIDTH = CALENDAR_RING_MAX_RADIUS - CALENDAR_RING_MIN_RADIUS

        private var debugMetricsPrinted = false

        private const val NON_LINEAR_TABLE_SIZE = 1000
        private val NON_LINEAR_TABLE = DoubleArray(NON_LINEAR_TABLE_SIZE) { i ->
            // This is where we initialize our non-linear second hand table.
            // We're implementing y=[(1+sin(theta - pi/2))/2] ^ pow over x in [0,pi]
            // Adjusting the power makes the second hand hang out closer to its
            // starting position and then launch faster to hit the target when we
            // get closer to the next second.
            val iFrac: Double = i.toDouble() / NON_LINEAR_TABLE_SIZE.toDouble()
            val thetaMinusPi2: Double = (iFrac - 0.5) * Math.PI

            // two components here: the non-linear part (with Math.pow and Math.sin) and then a linear
            // part (with iFrac). This make sure we still have some motion. The second hand never entirely stops.
            0.7 * Math.pow((1.0 + Math.sin(thetaMinusPi2)) / 2.0, 8.0) + 0.3 * iFrac
        }

        /**
         * This takes a value, in seconds, and makes the motion of the seconds-hand be *non-linear*
         * according to the pre-computed lookup table. The goal is to have the seconds-hand have a nice
         * snap to it, not unlike what you see in European train station clocks. Note that we're doing
         * a performance vs. memory tradeoff here. Rather than interpolating on the table, we're just
         * making the table bigger and clipping to the nearest table entry.
         */
        private fun nonLinearSeconds(linearSeconds: Double): Double {
            val secFloor = Math.floor(linearSeconds)
            val secFrac = linearSeconds - secFloor
            return secFloor + NON_LINEAR_TABLE[(secFrac * NON_LINEAR_TABLE_SIZE).toInt()]
        }

        // This counter increments once on every redraw and is used to log things that might otherwise
        // happen too frequently and fill the logs with crap.
        //
        // Typical usage: if(calendarTicker % 100 == 0) Log.v(...)
        private var calendarTicker = 0
    }
}
