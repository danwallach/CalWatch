/*
 * CalWatch
 * Copyright (C) 2014-2018 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch2

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import org.dwallach.calwatch2.BatteryWrapper.batteryPct
import org.dwallach.calwatch2.ClockState.FACE_LITE
import org.dwallach.calwatch2.ClockState.FACE_NUMBERS
import org.dwallach.calwatch2.ClockState.FACE_TOOL
import org.dwallach.calwatch2.ClockState.calendarPermission
import org.dwallach.calwatch2.ClockState.showDayDate
import org.dwallach.calwatch2.ClockState.showSeconds
import org.dwallach.calwatch2.PaintCan.Brush
import org.dwallach.calwatch2.PaintCan.Style
import org.dwallach.complications.ComplicationLocation.*
import org.dwallach.complications.ComplicationWrapper
import org.dwallach.complications.ComplicationWrapper.isVisible
import org.jetbrains.anko.*
import java.lang.Math.*
import java.util.Observable
import java.util.Observer

/**
 * All of the graphics calls for drawing watchfaces happen here. Note that this class knows
 * nothing about Android widgets, views, or activities. That stuff is handled in CalWatchFaceService.
 */
class ClockFace(val configMode: Boolean = false): Observer, AnkoLogger {
    // an instance of the ClockFace class is created anew alongside the rest of the UI; this number
    // helps us keep track of which instance is which
    private val instanceID: Int

    // initial values to get the ball rolling (avoids a div by zero problem in computeFlatBottomCorners)
    private var cx = DEFAULT_CX
    private var oldCx = -1
    private var cy = DEFAULT_CY
    private var oldCy = -1
    private var radius = DEFAULT_RADIUS

    private var flatBottomCornerTime = 30f // Moto 360 hack: set to < 30.0 seconds for where the flat bottom starts

    private var eventList: List<EventWrapper> = emptyList()
    private var maxLevel: Int = 0



    private var drawStyle = Style.NORMAL // see updateDrawStyle

    // dealing with the "flat tire" a.k.a. "chin" of Moto 360 and any other watches that pull the same trick
    var missingBottomPixels = 0
        set(newVal) {
            field = if (FORCE_MOTO_FLAT_BOTTOM) 30 else newVal
            computeFlatBottomCorners()
        }

    /**
     * Tell the clock face if we're in "mute" mode. For now, we don't care.
     */
    var muteMode: Boolean = false
        set(newVal) {
            verbose { "setMuteMode: $newVal" }
            field = newVal
        }

    /**
     * If true, ambient redrawing will be purely black and white, without any anti-aliasing (default: off).
     */
    var ambientLowBit = FORCE_AMBIENT_LOW_BIT
        set(newVal) {
            field = newVal || FORCE_AMBIENT_LOW_BIT
            verbose { "ambient low bit: $field" }
            updateDrawStyle()
        }

    private fun updateDrawStyle() {
        drawStyle = when {
            ambientMode && ambientLowBit && burnInProtection -> Style.LOWBIT_ANTI_BURNIN
            ambientMode && ambientLowBit -> Style.LOWBIT
            ambientMode && burnInProtection -> Style.AMBIENT_ANTI_BURNIN
            ambientMode -> Style.AMBIENT
            else -> Style.NORMAL
        }
    }

    private fun burninProtectionMode() = when (drawStyle) {
        Style.NORMAL, Style.AMBIENT -> false
        else -> true
    }

    init {
        instanceID = instanceCounter++
        verbose { "ClockFace setup, instance($instanceID)" }

        ClockState.addObserver(this) // so we get callbacks when the clock state changes
        update(null, null) // and we'll do that callback for the first time, just to initialize things
        missingBottomPixels = 0 // just to get things started; flat bottom detection happens later
    }

    /**
     * Set this at initialization time with the icon for the missing calendar.
     */
    var missingCalendarDrawable: Drawable? = null
        set(drawable) {
            field = drawable
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
     * Restricted version of [drawEverything] for use in [ClockFaceConfigView], which only
     * needs to draw the background and nothing else.
     */
    fun drawBackgroundOnly(canvas: Canvas) {
        drawFace(canvas)
        if (showDayDate) drawMonthBox(canvas)
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

            val currentTimeMillis = TimeWrapper.gmtTime

            // First thing, we'll draw any background that the complications might be providing, but only
            // if we're in "normal" mode. Note that we're not doing complication rendering unless we have
            // the calendar permission.

            // NOTE: disabled for now until we figure out a better way of doing partial transparency
            // for the calendar pie wedges.

//            if (drawStyle == Style.NORMAL && calendarPermission)
//                ComplicationWrapper.drawBackgroundComplication(canvas, currentTimeMillis)

            // the calendar goes on the very bottom and everything else stacks above; the code for calendar drawing
            // works great in low-bit mode, leaving big white wedges, but this violates the rules, per Google:

            // "For OLED screens, you must use a black background. Non-background pixels must be less than 10
            // percent of total pixels. You can use low-bit color for up to 5 percent of pixels on screens that support it."
            // -- http://developer.android.com/design/wear/watchfaces.html

            // NOTE: We used to also do this for the "AMBIENT" style, but we're removing it so
            // more of the screen is black, and thus we save power on OLED screens.
            if (drawStyle == Style.NORMAL) drawCalendar(canvas)

            // next, we draw the indices or numbers of the watchface
            drawFace(canvas)

            // Next up, the step counter and battery meter.

            // We disable the battery meter when we're in ambientMode with burnInProtection, since
            // we don't want to burn a hole in the very center of the screen.
            if (!burninProtectionMode()) drawBattery(canvas)

            // We're drawing the complications *before* the hands. We tried it after, but it
            // looks awful in ambient mode.

            // (If we don't have calendar permission, then we'll be insisting on that before
            // we do any complications. Any click will cause a permission dialog. Good UX?)
            if (calendarPermission)
                ComplicationWrapper.drawComplications(canvas, currentTimeMillis)

            drawHands(canvas)

            // something a real watch can't do: float the text over the hands
            // (but disable when we're in ambientMode with burnInProtection)
            if (showDayDate) drawMonthBox(canvas)

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

        /***
         * Killing this off for now. We've done lots of other goodies for anti-burnin, and
         * this really hurts readability on a small screen.
         *
        if (drawStyle == Style.AMBIENT_ANTI_BURNIN || drawStyle == Style.LOWBIT_ANTI_BURNIN) {
            // scale down everything to leave a 10 pixel margin

            val ratio = (radius - 10f) / radius
            startRadius *= ratio
            endRadius *= ratio
            strokeWidth *= ratio
        }
        ***/

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
    private fun drawRadialArc(canvas: Canvas, path: Path?, secondsStart: Double, secondsEnd: Double, startRadiusRatio: Float, endRadiusRatio: Float, paint: Paint, outlinePaint: Paint?): Path {
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

        var p: Path? = path

        if (p == null) {
            p = Path()

            val midOval = getRectRadius((startRadius + endRadius) / 2f + 0.025f)
            val midOvalDelta = getRectRadius((startRadius + endRadius) / 2f - 0.025f)
            val startOval = getRectRadius(startRadius)
            val endOval = getRectRadius(endRadius)
            p.arcTo(startOval, (secondsStart * 6 - 90).toFloat(), ((secondsEnd - secondsStart) * 6).toFloat(), true)
            p.arcTo(endOval, (secondsEnd * 6 - 90).toFloat(), (-(secondsEnd - secondsStart) * 6).toFloat())
            p.close()
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

        val paint = PaintCan[drawStyle, Brush.MONTHBOX_TEXT]
        val shadow = PaintCan[drawStyle, Brush.MONTHBOX_SHADOW]

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

    private var facePathCache: Path? = null
    private var facePathComplicationState: Int = 0
    private var facePathCacheMode = -1

    private fun complicationStateNow(): Int =
            (if (isVisible(LEFT)) 8 else 0) + (if (isVisible(RIGHT)) 4 else 0) + (if (isVisible(TOP)) 2 else 0) + (if (isVisible(BOTTOM)) 1 else 0)

    private fun getCachedFacePath(mode: Int): Path? =
        if (facePathComplicationState == complicationStateNow() && facePathCacheMode == mode) facePathCache else null

    private fun saveCachedFacePath(mode: Int, path: Path) {
        facePathCacheMode = mode
        facePathCache = path
        facePathComplicationState = complicationStateNow()
    }

    private fun drawFace(canvas: Canvas) {
        val bottomHack = missingBottomPixels > 0

        // force "lite" mode when in burn-in protection mode
//        val lFaceMode = if(drawStyle == Style.LOWBIT_ANTI_BURNIN) FACE_LITE else ClockState.faceMode

        // see if we can make this work properly without needing to drop into "lite" mode
        val lFaceMode = ClockState.faceMode
        var lFacePathCache = getCachedFacePath(lFaceMode)

        val colorTickShadow = PaintCan[drawStyle, Brush.TICK_SHADOW]
        val colorSmall = PaintCan[drawStyle, Brush.SMALL_LINES]
        val colorBig = PaintCan[drawStyle, Brush.BIG_TEXT_AND_LINES]
        val colorTextShadow = PaintCan[drawStyle, Brush.BIG_SHADOW]

        // check if we've already rendered the face
        if (lFacePathCache == null) {
            verbose { "drawFace: cx($cx), cy($cy), r($radius)" }

            lFacePathCache = Path()

            verbose { "rendering new face, faceMode($lFaceMode)" }

            if (calendarTicker % 1000 == 0) {
                verbose { "Complication visibility map: (LEFT, RIGHT, TOP, BOTTOM) -> ${isVisible(LEFT)}, ${isVisible(RIGHT)}, ${isVisible(TOP)}, ${isVisible(BOTTOM)}" }
            }

            if (lFaceMode == FACE_TOOL)
                for (i in 1..59) {
                    if (bottomHack && isVisible(BOTTOM) && i >= 26 && i <= 34) continue // don't even bother if flat bottom and complication

                    if (i % 5 != 0)
                        drawRadialLine(lFacePathCache, colorSmall.strokeWidth, i.toDouble(), 0.9f, 1.0f, false, bottomHack)
                }

            val strokeWidth =
                    if (lFaceMode == FACE_LITE || lFaceMode == FACE_NUMBERS || drawStyle != Style.NORMAL)
                        colorSmall.strokeWidth
                    else
                        colorBig.strokeWidth

            if (lFaceMode != FACE_NUMBERS) {
                // top of watch
                val topLineStart = if (isVisible(TOP)) 0.85f else 0.75f

                // we draw double lines here, because style
                drawRadialLine(lFacePathCache, strokeWidth, -0.4, topLineStart, 1.0f, true, false)
                drawRadialLine(lFacePathCache, strokeWidth, 0.4, topLineStart, 1.0f, true, false)

                // left of watch
                if (showDayDate) {
                    drawRadialLine(lFacePathCache, strokeWidth, 45.0, 0.9f, 1.0f, false, false)
                } else {
                    drawRadialLine(lFacePathCache, strokeWidth, 45.0, 0.75f, 1.0f, false, false)
                }

                // right of watch
                val rightLineStart = if (isVisible(RIGHT)) 0.85f else 0.75f
                drawRadialLine(lFacePathCache, strokeWidth, 15.0, rightLineStart, 1.0f, false, bottomHack)

                // bottom of watch
                if (!isVisible(BOTTOM) || !bottomHack) { // don't even bother if we've got a flat tire *and* a complication
                    val bottomLineStart = when {
                        isVisible(BOTTOM) -> 0.85f
                        bottomHack -> 0.9f
                        else -> 0.75f
                    }
                    drawRadialLine(lFacePathCache, strokeWidth, 30.0, bottomLineStart, 1.0f, false, bottomHack)
                }
            }

            // and the rest of the hour indicators!
            for (i in 5..59 step 5) {
                if (i == 15 || i == 30 || i == 45) continue

                drawRadialLine(lFacePathCache, strokeWidth, i.toDouble(), 0.75f, 1.0f, false, bottomHack)
            }

            saveCachedFacePath(lFaceMode, lFacePathCache)
        }

        canvas.drawPath(lFacePathCache, colorSmall)
        canvas.drawPath(lFacePathCache, colorTickShadow)

        if (lFaceMode == FACE_NUMBERS) {
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
            if (!isVisible(TOP)) { // don't draw if there's a complication
                r = 0.9f

                x = clockX(0.0, r)
                y = clockY(0.0, r) - metrics.ascent / 1.5f

                drawShadowText(canvas, "12", x, y, colorBig, colorTextShadow)

                if (!debugMetricsPrinted) {
                    debugMetricsPrinted = true
                    verbose { "x(%.2f), y(%.2f), metrics.descent(%.2f), metrics.asacent(%.2f)".format(x, y, metrics.descent, metrics.ascent) }
                }
            }

            //
            // 3 o'clock
            //

            if (!isVisible(RIGHT)) { // don't draw if there's a complication
                r = 0.9f

                val threeWidth = colorBig.measureText("3")

                x = clockX(15.0, r) - threeWidth / 2f
                y = clockY(15.0, r) - metrics.ascent / 2f - metrics.descent / 2f // empirically gets the middle of the "3" -- actually a smidge off with Roboto but close enough for now and totally font-dependent with no help from metrics

                drawShadowText(canvas, "3", x, y, colorBig, colorTextShadow)
            }

            //
            // 6 o'clock
            //

            if (!isVisible(BOTTOM)) { // don't draw if there's a complication
                r = 0.9f

                x = clockX(30.0, r)
                y = if (missingBottomPixels != 0)
                    clockY(30.0, r) + metrics.descent - missingBottomPixels // another hack for Moto 360
                else
                    clockY(30.0, r) + 0.75f * metrics.descent // scoot it up a tiny bit

                drawShadowText(canvas, "6", x, y, colorBig, colorTextShadow)
            }

            //
            // 9 o'clock
            //

            if (!showDayDate) { // don't draw if our internal complication is visible
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


        val shadowColor = PaintCan[drawStyle, Brush.HAND_SHADOW]
        val hourColor = PaintCan[drawStyle, Brush.HOUR_HAND]
        val minuteColor = PaintCan[drawStyle, Brush.MINUTE_HAND]

        drawRadialLine(canvas, hours, 0.1f, 0.6f, hourColor, shadowColor)
        drawRadialLine(canvas, minutes, 0.1f, 0.9f, minuteColor, shadowColor)

        if (drawStyle == Style.NORMAL && showSeconds) {
            val secondsColor = PaintCan[Style.NORMAL, Brush.SECOND_HAND]
            // Cleverness: we're doing "nonLinearSeconds" which gives us the funky sweeping
            // behavior that European rail clocks demonstrate. The only thing that "real" rail
            // clocks have the we don't is a two second hard stop at 12 o'clock.

            drawRadialLine(canvas, nonLinearSeconds(seconds), 0.1f, 0.95f, secondsColor, shadowColor)
        }
    }

    /**
     * call this if external forces at play may have invalidated state
     * being saved inside ClockFace
     */
    private fun wipeCaches() {
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
        if (!calendarPermission && drawStyle == Style.NORMAL) {
            missingCalendarDrawable?.draw(canvas)
            return
        }

        val time = TimeWrapper.localTime

        eventList.forEach {
            val e = it.calendarEvent
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

            val arcColor = it.paint
            val arcShadow = PaintCan[drawStyle, Brush.ARC_SHADOW]

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
        val lStipplePathCache = stipplePathCache ?: Path()
        if (stippleTime != stippleTimeCache || stipplePathCache == null) {
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
        canvas.drawPath(lStipplePathCache, PaintCan[drawStyle, Brush.BLACK_FILL])
    }

    private var batteryPathCache: Path? = null
    private var batteryCritical = false
    private var batteryCacheTime: Long = 0

    private fun drawBattery(canvas: Canvas) {
        val time = TimeWrapper.gmtTime

        // we don't want to poll *too* often; the code below translates to about once per five minute
        val lBatteryPathCache = batteryPathCache ?: Path()
        if (batteryPathCache == null || time - batteryCacheTime > 5.minutes) {
            verbose("fetching new battery status")
            BatteryWrapper.fetchStatus()
            batteryCacheTime = time

            //
            // The concept: draw nothing unless the battery is low. At 30% (POWER_WARN_LOW_LEVEL),
            // we start a small yellow circle. This scales in radius until it hits max size at 10%
            // (POWER_WARN_CRITICAL_LEVEL), then it switches to red.
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
            val paint = PaintCan[drawStyle, if (batteryCritical) Brush.BATTERY_CRITICAL else Brush.BATTERY_LOW]
            canvas.drawPath(lBatteryPathCache, paint)
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

        ClockState.screenX = cx
        ClockState.screenY = cy

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
            val angle = asin(1.0 - missingBottomPixels.toDouble() / cy.toDouble())

            flatBottomCornerTime = (angle * 30.0 / PI + 15).toFloat()
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
        val angleRadians = (seconds - 15) * PI * 2.0 / 60.0
        return (cx + radius.toDouble() * fractionFromCenter.toDouble() * cos(angleRadians)).toFloat()
    }

    private fun clockY(seconds: Double, fractionFromCenter: Float): Float {
        val angleRadians = (seconds - 15) * PI * 2.0 / 60.0
        return (cy + radius.toDouble() * fractionFromCenter.toDouble() * sin(angleRadians)).toFloat()
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
            val angleRadians = (seconds - 15) * PI * 2.0 / 60.0
            return try {
                ((cy - missingBottomPixels) / (radius * sin(angleRadians))).toFloat()
            } catch (e: ArithmeticException) {
                // division by zero, weird, so fall back to the default
                1f
            }

        } else
            return 1f
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

    /**
     * Tracking whether or not we're in ambient mode.
     */
    var ambientMode = false
        set(newVal) {
            info { "Ambient mode: $field -> $newVal" }
            if (field == newVal) return // nothing changed, so we're good
            field = newVal

            updateDrawStyle()
            wipeCaches()

        }

    /**
     * Tracking whether or not we need to be in burnin-protection mode.
     */
    var burnInProtection = FORCE_BURNIN_PROTECTION
        set(newVal) {
            field = newVal || FORCE_BURNIN_PROTECTION
            updateDrawStyle()
        }

    /**
     * Let us know if we're on a square or round watchface. (We don't really care. For now.)
     */
    var round: Boolean = false
        set(newVal) {
            field = newVal
            verbose { "setRound: $field" }
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
            // We're implementing y=[(1+sin(theta - pi/2))/2] ^ pow over theta in [0,pi]
            // Adjusting the power makes the second hand hang out closer to its
            // starting position and then launch faster to hit the target when we
            // get closer to the next second.
            val iFrac: Double = i.toDouble() / NON_LINEAR_TABLE_SIZE.toDouble()
            val thetaMinusPi2: Double = (iFrac - 0.5) * PI

            // two components here: the non-linear part (with Math.pow and Math.sin) and then a linear
            // part (with iFrac). This make sure we still have some motion. The second hand never entirely stops.
            0.7 * pow((1.0 + sin(thetaMinusPi2)) / 2.0, 8.0) + 0.3 * iFrac
        }

        /**
         * This takes a value, in seconds, and makes the motion of the seconds-hand be *non-linear*
         * according to the pre-computed lookup table. The goal is to have the seconds-hand have a nice
         * snap to it, not unlike what you see in European train station clocks. Note that we're doing
         * a performance vs. memory tradeoff here. Rather than interpolating on the table, we're just
         * making the table bigger and clipping to the nearest table entry.
         */
        private fun nonLinearSeconds(linearSeconds: Double): Double {
            val secFloor = floor(linearSeconds)
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
