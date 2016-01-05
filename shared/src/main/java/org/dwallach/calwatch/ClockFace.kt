/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import java.util.Observable
import java.util.Observer

class ClockFace : Observer {
    private val instanceID: Int

    // initial values to get the ball rolling (avoids a div by zero problem in computeFlatBottomCorners)
    private var cx = savedCx
    private var oldCx = -1
    private var cy = savedCy
    private var oldCy = -1
    private var radius = savedRadius

    private var showSeconds = true
    private var showDayDate = true
    private var ambientLowBit = forceAmbientLowBit
    private var burnInProtection = forceBurnInProtection

    private var missingBottomPixels = 0 // Moto 360 hack; set to non-zero number to pull up the indicia
    private var flatBottomCornerTime = 30f // Moto 360 hack: set to < 30.0 seconds for where the flat bottom starts

    private val clockState: ClockState
    private var peekCardRect: Rect? = null
    private var missingCalendarX: Float = 0.toFloat()
    private var missingCalendarY: Float = 0.toFloat()
    private var missingCalendarBitmap: Bitmap? = null

    // dealing with the "flat tire" a.k.a. "chin" of Moto 360 and any other watches that pull the same trick
    fun setMissingBottomPixels(missingBottomPixels: Int) {
        if (forceMotoFlatBottom)
            this.missingBottomPixels = 30
        else
            this.missingBottomPixels = missingBottomPixels

        computeFlatBottomCorners()
    }

    fun setPeekCardRect(rect: Rect) {
        peekCardRect = rect
    }

    /**
     * Tell the clock face if we're in "mute" mode. Unclear we want to actually do anything different.
     */
    fun setMuteMode(muteMode: Boolean) {
    }

    /**
     * If true, ambient redrawing will be purely black and white, without any anti-aliasing (default: off).
     */
    fun setAmbientLowBit(ambientLowBit: Boolean) {
        Log.v(TAG, "ambient low bit: " + ambientLowBit)
        this.ambientLowBit = ambientLowBit || forceAmbientLowBit
    }

    init {
        instanceID = instanceCounter++
        Log.v(TAG, "ClockFace setup, instance($instanceID)")

        this.clockState = ClockState.getState()
        setupObserver()
        update(null, null) // initialize variables from initial constants, or whatever else is hanging out in ClockState

        // note: this space used to detect if the Build.MODEL and Build.PRODUCT matched a Moto360 and
        // called setMissingBottomPixels(). We're removing that now that there are several different
        // watches with the same property.

        // This is just to get things started. Because of Wear 5, there will be another call
        // once the watchface is set up and running.
        setMissingBottomPixels(0)
    }

    /**
     * Call this at initialization time to set up the icon for the missing calendar.
     */
    fun setMissingCalendarBitmap(bitmap: Bitmap) {
        missingCalendarBitmap = bitmap

        updateMissingCalendarRect()
    }


    private fun updateMissingCalendarRect() {
        val height = missingCalendarBitmap!!.height
        val width = missingCalendarBitmap!!.width

        missingCalendarX = clockX(15.0, 0.2f)
        missingCalendarY = clockY(0.0, 0f) - height / 2

        Log.v(TAG, "missing calendar bitmap size: (%d,%d), coordinates: (%.1f,%.1f),  (cy: %d, radius: %d)".format(width, height, missingCalendarX, missingCalendarY, cy, radius))
    }

    /*
     * the expectation is that you call this method *not* from the UI thread but instead
     * from a helper thread, elsewhere
     */
    fun drawEverything(canvas: Canvas) {
        TimeWrapper.frameStart()

        // the calendar goes on the very bottom and everything else stacks above

        drawCalendar(canvas)


        drawFace(canvas)

        // okay, we're drawing the stopwatch and countdown timers next: they've got partial transparency
        // that will let the watchface tick marks show through, except they're opaque in low-bit mode

        drawTimers(canvas)

        // Kludge for peek card until we come up with something better:
        // if there's a peek card *and* we're in ambient mode, *then* draw
        // a solid black box behind the peek card, which would otherwise be transparent.
        // Note that we're doing this *before* drawing the hands but *after* drawing
        // everything else. I want the hands to not be chopped off, even though everything
        // else will be.
        if (peekCardRect != null && ambientMode)
            canvas.drawRect(peekCardRect, PaintCan.get(ambientLowBit, ambientMode, PaintCan.colorBlackFill))

        drawHands(canvas)

        // something a real watch can't do: float the text over the hands
        // (note that we don't do this at all in ambient mode)
        if (!ambientMode && showDayDate) drawMonthBox(canvas)

        // and lastly, the battery meter
        // (not in ambient mode, because we don't want to cause burn-in)
        if (!ambientMode) drawBattery(canvas)

        TimeWrapper.frameEnd()
    }

    private fun drawRadialLine(canvas: Canvas, seconds: Double, startRadius: Float, endRadius: Float, paint: Paint, shadowPaint: Paint?, forceVertical: Boolean = false) {
        val p = Path()
        drawRadialLine(p, paint.strokeWidth, seconds, startRadius, endRadius, forceVertical, false)
        canvas.drawPath(p, paint)
        if (shadowPaint != null)
            canvas.drawPath(p, shadowPaint)
    }

    private fun drawRadialLine(path: Path, strokeWidth: Float, seconds: Double, startRadius: Float, endRadius: Float, forceVertical: Boolean, flatBottomHack: Boolean) {
        var seconds = seconds
        var startRadius = startRadius
        var endRadius = endRadius
        val x1: Float
        var x2: Float
        val y1: Float
        val y2: Float

        if (flatBottomHack) {
            val clipRadius = radiusToEdge(seconds)
            if (endRadius > clipRadius) {
                val dr = endRadius - clipRadius
                startRadius -= dr
                endRadius -= dr
            }
        }

        if (burnInProtection && ambientMode) {
            // scale down everything to leave a 10 pixel margin

            val ratio = (radius - 10f) / radius
            startRadius *= ratio
            endRadius *= ratio
        }

        x1 = clockX(seconds, startRadius)
        y1 = clockY(seconds, startRadius)
        x2 = clockX(seconds, endRadius)
        y2 = clockY(seconds, endRadius)
        if (forceVertical) {
            seconds = 0.0
            x2 = x1
        }

        val dx = (clockX(seconds + 15, 1f) - cx) * 0.5f * strokeWidth / radius
        val dy = (clockY(seconds + 15, 1f) - cy) * 0.5f * strokeWidth / radius

        path.moveTo(x1 + dx, y1 + dy)
        path.lineTo(x2 + dx, y2 + dy)
        path.lineTo(x2 - dx, y2 - dy)
        path.lineTo(x1 - dx, y1 - dy)
        // path.lineTo(x1+dx, y1+dy);
        path.close()
    }

    private fun getRectRadius(radius: Float): RectF {
        return RectF(
                clockX(45.0, radius), // left
                clockY(0.0, radius), // top
                clockX(15.0, radius), // right
                clockY(30.0, radius))// bottom
    }

    private fun drawRadialArc(canvas: Canvas, pc: PathCache?, secondsStart: Double, secondsEnd: Double, startRadius: Float, endRadius: Float, paint: Paint, outlinePaint: Paint?, lowBitSquish: Boolean = true) {
        var startRadius = startRadius
        var endRadius = endRadius
        /*
         * Below is an attempt to do this "correctly" using the arc functionality supported natively
         * by Android's Path.
         */

        if (startRadius < 0 || startRadius > 1 || endRadius < 0 || endRadius > 1) {
            Log.e(TAG, "arc too big! radius(" + java.lang.Float.toString(startRadius) + "," + java.lang.Float.toString(endRadius) +
                    "), seconds(" + java.lang.Float.toString(secondsStart.toFloat()) + "," + java.lang.Float.toString(secondsEnd.toFloat()) + ")")
        }

        if (burnInProtection && ambientMode) {
            // scale down everything to leave a 10 pixel margin

            val ratio = (radius - 10f) / radius

            startRadius *= ratio
            endRadius *= ratio
        }

        var p: Path? = null

        if (pc != null) p = pc.get()

        if (p == null) {
            p = Path()

            val midOval = getRectRadius((startRadius + endRadius) / 2f + 0.025f)
            val midOvalDelta = getRectRadius((startRadius + endRadius) / 2f - 0.025f)
            val startOval = getRectRadius(startRadius)
            val endOval = getRectRadius(endRadius)
            if (ambientMode && ambientLowBit && lowBitSquish) {
                // in ambient low-bit mode, we're going to draw some slender arcs of fixed width at roughly the center of the big
                // colored pie wedge which we normally show when we're not in ambient mode
                p.arcTo(midOval, (secondsStart * 6 - 90).toFloat(), ((secondsEnd - secondsStart) * 6).toFloat(), true)
                p.arcTo(midOvalDelta, (secondsEnd * 6 - 90).toFloat(), (-(secondsEnd - secondsStart) * 6).toFloat())
                p.close()
            } else {
                p.arcTo(startOval, (secondsStart * 6 - 90).toFloat(), ((secondsEnd - secondsStart) * 6).toFloat(), true)
                p.arcTo(endOval, (secondsEnd * 6 - 90).toFloat(), (-(secondsEnd - secondsStart) * 6).toFloat())
                p.close()

                //            Log.e(TAG, "New arc: radius(" + Float.toString((float) startRadius) + "," + Float.toString((float) endRadius) +
                //                    "), seconds(" + Float.toString((float) secondsStart) + "," + Float.toString((float) secondsEnd) + ")");
                //            Log.e(TAG, "--> arcTo: startOval, " + Float.toString((float) (secondsStart * 6 - 90)) + ", " +  Float.toString((float) ((secondsEnd - secondsStart) * 6)));
                //            Log.e(TAG, "--> arcTo: endOval, " + Float.toString((float) (secondsEnd * 6 - 90)) + ", " +  Float.toString((float) (-(secondsEnd - secondsStart) * 6)));
            }

            pc?.set(p)
        }

        canvas.drawPath(p, paint)
        canvas.drawPath(p, outlinePaint)
    }

    //    private int flatCounter = 0;
    private fun drawRadialArcFlatBottom(canvas: Canvas, seconds: Float, startRadius: Float, endRadius: Float, paint: Paint, outlinePaint: Paint?) {
        var startRadius = startRadius
        var endRadius = endRadius
        //        flatCounter++;

        if (startRadius < 0 || startRadius > 1 || endRadius < 0 || endRadius > 1) {
            Log.e(TAG, "drawRadialArcFlatBottom: arc too big! radius($startRadius,$endRadius), seconds($seconds)")
            return
        }

        if (seconds < 0 || seconds > 60) {
            Log.e(TAG, "drawRadialArcFlatBottom: seconds out of range: " + seconds)
            return
        }

        if (burnInProtection && ambientMode) {
            // scale down everything to leave a 10 pixel margin
            val ratio = (radius - 10f) / radius

            startRadius *= ratio
            endRadius *= ratio
        }

        // This one always starts at the top and goes clockwise to the time indicated (seconds).
        // This computation is much easier than doing it for the general case.

        if (missingBottomPixels == 0)
            drawRadialArc(canvas, null, 0.0, seconds.toDouble(), startRadius, endRadius, paint, outlinePaint, false)
        else {
            val p = Path()

            val startOval = getRectRadius(startRadius)
            val endOval = getRectRadius(endRadius)

            if (seconds < flatBottomCornerTime) {
                // trivial case: we don't run into the flat tire region

                //                if(flatCounter % 100 == 1) {
                //                    Log.v(TAG, "flat (case 1): seconds(" + seconds + ") < bottomCornerTime(" + flatBottomCornerTime + ")");
                //                }
                p.arcTo(startOval, -90f, seconds * 6f, true)
                p.arcTo(endOval, seconds * 6f - 90f, -seconds * 6f)
            } else if (seconds < 60 - flatBottomCornerTime) {
                // next case: we're inside the flat-bottom region

                //                if(flatCounter % 100 == 1) {
                //                    Log.v(TAG, "flat (case 2): seconds(" + seconds + ") < bottomCornerTime(" + flatBottomCornerTime + ")");
                //                }

                p.arcTo(startOval, -90f, flatBottomCornerTime * 6f, true)

                // lines left then down
                p.lineTo(flatBottomX(seconds, startRadius), flatBottomY(seconds, startRadius)) // sideways
                p.lineTo(flatBottomX(seconds, endRadius), flatBottomY(seconds, endRadius)) // change levels

                // this will automatically jump us back to the right before going back up again
                p.arcTo(endOval, flatBottomCornerTime * 6f - 90f, -flatBottomCornerTime * 6f)

            } else {
                // final case, we're covering the entire initial arc, all the way across the flat bottom
                // (with a linear discontinuity, but arcTo() will bridge the gap with a lineTo())
                // then up the other side

                //                if(flatCounter % 100 == 1) {
                //                    Log.v(TAG, "flat (case 3): seconds(" + seconds + ") < bottomCornerTime(" + flatBottomCornerTime + ")");
                //                }
                p.arcTo(startOval, -90f, flatBottomCornerTime * 6f, true)
                p.arcTo(startOval, -90f + 6f * (60 - flatBottomCornerTime), 6f * (seconds - 60 + flatBottomCornerTime))

                // okay, we're up on the left side, need to work our way back down again
                p.arcTo(endOval, -90f + 6f * seconds, 6f * (60f - flatBottomCornerTime - seconds))
                p.arcTo(endOval, -90f + 6f * flatBottomCornerTime, -6f * flatBottomCornerTime)
            }
            p.close()

            canvas.drawPath(p, paint)
            if (outlinePaint != null)
                canvas.drawPath(p, outlinePaint)
        }
    }

    private fun drawMonthBox(canvas: Canvas) {
        // for now, hard-coded to the 9-oclock position
        val m = TimeWrapper.localMonthDay()
        val d = TimeWrapper.localDayOfWeek()
        val x1: Float
        val y1: Float
        x1 = clockX(45.0, .85f)
        y1 = clockY(45.0, .85f)

        val paint = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSmallTextAndLines]
        val shadow = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSmallShadow]

        // AA note: we only draw the month box when in normal mode, not ambient, so no AA gymnastics here

        val metrics = paint.fontMetrics
        val dybottom = -metrics.ascent - metrics.leading // smidge it up a bunch
        val dytop = -metrics.descent // smidge it down a little

        drawShadowText(canvas, d, x1, y1 + dybottom, paint, shadow)
        drawShadowText(canvas, m, x1, y1 + dytop, paint, shadow)
    }

    private fun drawShadowText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, shadowPaint: Paint) {
        canvas.drawText(text, x, y, shadowPaint)
        canvas.drawText(text, x, y, paint)
    }

    @Volatile private var facePathCache: Path? = null
    @Volatile private var facePathCacheMode = -1

    private fun drawFace(canvas: Canvas) {
        var p = facePathCache // make a local copy, avoid concurrency crap
        // draw thin lines (indices)

        val bottomHack = missingBottomPixels > 0

        val localFaceMode = faceMode

        val colorTickShadow = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSecondHandShadow]
        val colorSmall = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSmallTextAndLines]
        val colorBig = PaintCan[ambientLowBit, ambientMode, PaintCan.colorBigTextAndLines]
        val colorTextShadow = PaintCan[ambientLowBit, ambientMode, PaintCan.colorBigShadow]

        // check if we've already rendered the face
        if (localFaceMode != facePathCacheMode || p == null) {
            Log.v(TAG, "drawFace: cx($cx), cy($cy), r($radius)")

            p = Path()

            Log.v(TAG, "rendering new face, faceMode($localFaceMode)")

            if (localFaceMode == ClockState.FACE_TOOL)
                for (i in 1..59)
                    if (i % 5 != 0)
                        drawRadialLine(p, colorSmall.strokeWidth, i.toDouble(), .9f, 1.0f, false, bottomHack)

            val strokeWidth: Float

            if (localFaceMode == ClockState.FACE_LITE || localFaceMode == ClockState.FACE_NUMBERS)
                strokeWidth = colorSmall.strokeWidth
            else
                strokeWidth = colorBig.strokeWidth


            var i = 0
            while (i < 60) {
                if (i == 0) {
                    // top of watch: special
                    if (localFaceMode != ClockState.FACE_NUMBERS) {
                        drawRadialLine(p, strokeWidth, -0.4, .75f, 1.0f, true, false)
                        drawRadialLine(p, strokeWidth, 0.4, .75f, 1.0f, true, false)
                    }
                } else if (i == 45 && !ambientMode && showDayDate) {
                    // 9 o'clock, don't extend into the inside
                    drawRadialLine(p, strokeWidth, i.toDouble(), 0.9f, 1.0f, false, false)
                } else {
                    // we want lines for 1, 2, 4, 5, 7, 8, 10, and 11 no matter what
                    if (localFaceMode != ClockState.FACE_NUMBERS || !(i == 15 || i == 30 || i == 45)) {
                        // in the particular case of 6 o'clock and the Moto 360 bottomHack, we're
                        // going to make the 6 o'clock index line the same length as the other lines
                        // so it doesn't stand out as much
                        if (i == 30 && bottomHack)
                            drawRadialLine(p, strokeWidth, i.toDouble(), .9f, 1.0f, false, bottomHack)
                        else
                            drawRadialLine(p, strokeWidth, i.toDouble(), .75f, 1.0f, false, bottomHack)
                    }
                }
                i += 5
            }

            facePathCache = p
            facePathCacheMode = localFaceMode
        }

        canvas.drawPath(p, colorSmall)

        // only draw the shadows when we're in high-bit mode
        if (!ambientLowBit || !ambientMode)
            canvas.drawPath(p, colorTickShadow)

        if (localFaceMode == ClockState.FACE_NUMBERS) {
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
                Log.v(TAG, "x(" + java.lang.Float.toString(x) + "), y(" + java.lang.Float.toString(y) + "), metrics.descent(" + java.lang.Float.toString(metrics.descent) + "), metrics.ascent(" + java.lang.Float.toString(metrics.ascent) + ")")
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

            if (ambientMode || !showDayDate) {
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

        var seconds = time / 1000.0
        val minutes = seconds / 60.0
        val hours = minutes / 12.0  // because drawRadialLine is scaled to a 60-unit circle

        val hourColor: Paint
        val minuteColor: Paint
        val secondsColor: Paint
        val shadowColor: Paint

        shadowColor = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSecondHandShadow]
        hourColor = PaintCan[ambientLowBit, ambientMode, PaintCan.colorHourHand]
        minuteColor = PaintCan[ambientLowBit, ambientMode, PaintCan.colorMinuteHand]
//        secondsColor = PaintCan[ambientLowBit, ambientMode, PaintCan.colorSecondHand];

        drawRadialLine(canvas, hours, 0.1f, 0.6f, hourColor, shadowColor)
        drawRadialLine(canvas, minutes, 0.1f, 0.9f, minuteColor, shadowColor)

        if (!ambientMode && showSeconds) {
            secondsColor = PaintCan[PaintCan.styleNormal, PaintCan.colorSecondHand]
            // ugly details: we might run 10% or more away from our targets at 4Hz, making the second
            // hand miss the indices. Ugly. Thus, some hackery.
            if (clipSeconds) seconds = Math.floor(seconds * freqUpdate) / freqUpdate
            drawRadialLine(canvas, nonLinearSeconds(seconds), 0.1f, 0.95f, secondsColor, shadowColor)
        }
    }

    /**
     * call this if external forces at play may have invalidated state
     * being saved inside ClockFace
     */
    fun wipeCaches() {
        Log.v(TAG, "clearing caches")

        facePathCache = null
        batteryPathCache = null
        batteryCritical = false
        stipplePathCache = null
        stippleTimeCache = -1

        if (eventList != null)
            for (eventWrapper in eventList!!) {
                val pc = eventWrapper.pathCache
                pc.set(null)
            }
    }

    private var stippleTimeCache: Long = -1
    private var stipplePathCache: Path? = null

    private fun drawCalendar(canvas: Canvas) {
        calendarTicker++

        // if we don't have permission to see the calendar, then we'll let the user know
        if (!clockState.calendarPermission && missingCalendarBitmap != null && !ambientMode) {
            canvas.drawBitmap(missingCalendarBitmap, missingCalendarX, missingCalendarY, null)
            return
        }


        // this line represents a big change; we're still an observer of the clock state, but now
        // we're also polling it; it promises to support this polling efficiently, and in return,
        // we know we've always got an up to date set of calendar wedges
        updateEventList()

        if (eventList == null) {
            if (calendarTicker % 1000 == 0) Log.v(TAG, "drawCalendar starting, eventList is null")

            update(null, null) // probably won't accomplish any more than the updateEventList above...

            if (eventList == null) {
                Log.v(TAG, "eventList still null after update; giving up")
                return  // again, must not be ready yet
            }
        }

        val time = TimeWrapper.localTime

        for (eventWrapper in eventList!!) {
            val arcStart: Double
            val arcEnd: Double
            val e = eventWrapper.wireEvent
            val evMinLevel = eventWrapper.minLevel
            val evMaxLevel = eventWrapper.maxLevel

            val startTime = e.startTime!!
            val endTime = e.endTime!!

            arcStart = startTime / 720000.0
            arcEnd = endTime / 720000.0

            // path caching happens inside drawRadialArc

            val arcColor = eventWrapper.getPaint(ambientLowBit, ambientMode)
            val arcShadow = PaintCan[ambientLowBit, ambientMode, PaintCan.colorArcShadow]

            drawRadialArc(canvas, eventWrapper.pathCache, arcStart, arcEnd,
                    calendarRingMaxRadius - evMinLevel * calendarRingWidth / (maxLevel + 1),
                    calendarRingMaxRadius - (evMaxLevel + 1) * calendarRingWidth / (maxLevel + 1),
                    arcColor, arcShadow)
        }

        // Lastly, draw a stippled pattern at the current hour mark to delineate where the
        // twelve-hour calendar rendering zone starts and ends.


        // integer division gets us the exact hour, then multiply by 5 to scale to our
        // 60-second circle
        var stippleTime = time / (1000 * 60 * 60)
        stippleTime *= 5

        // we might want to rejigger this to be paranoid about concurrency smashing stipplePathCache,
        // but it's less of a problem here than with the watchFace, because the external UI isn't
        // inducing the state here to change
        if (stippleTime != stippleTimeCache || stipplePathCache == null) {
            stipplePathCache = Path()
            stippleTimeCache = stippleTime

            //            if(calendarTicker % 1000 == 0)
            //                Log.v(TAG, "StippleTime(" + stippleTime +
            //                        "),  currentTime(" + Float.toString((time) / 720000f) + ")");

            var r1: Float
            var r2: Float

            // eight little diamonds -- precompute the deltas when we're all the way out at the end,
            // then apply elsewhere

            val dxlow: Float
            val dylow: Float
            val dxhigh: Float
            val dyhigh: Float
            var x1: Float
            var y1: Float
            var x2: Float
            var y2: Float
            var xlow: Float
            var ylow: Float
            var xmid: Float
            var ymid: Float
            var xhigh: Float
            var yhigh: Float
            val stippleWidth = 0.3f
            val stippleSteps = 8
            val rDelta = calendarRingWidth / stippleSteps.toFloat()

            x1 = clockX(stippleTime.toDouble(), calendarRingMaxRadius)
            y1 = clockY(stippleTime.toDouble(), calendarRingMaxRadius)
            x2 = clockX(stippleTime.toDouble(), calendarRingMaxRadius - rDelta)
            y2 = clockY(stippleTime.toDouble(), calendarRingMaxRadius - rDelta)
            xmid = (x1 + x2) / 2f
            ymid = (y1 + y2) / 2f
            xlow = clockX((stippleTime - stippleWidth).toDouble(), calendarRingMaxRadius - rDelta / 2)
            ylow = clockY((stippleTime - stippleWidth).toDouble(), calendarRingMaxRadius - rDelta / 2)
            xhigh = clockX((stippleTime + stippleWidth).toDouble(), calendarRingMaxRadius - rDelta / 2)
            yhigh = clockY((stippleTime + stippleWidth).toDouble(), calendarRingMaxRadius - rDelta / 2)
            dxlow = xmid - xlow
            dylow = ymid - ylow
            dxhigh = xmid - xhigh
            dyhigh = ymid - yhigh

            r1 = calendarRingMinRadius
            x1 = clockX(stippleTime.toDouble(), r1)
            y1 = clockY(stippleTime.toDouble(), r1)
            var i = 0
            while (i < 8) {
                r2 = r1 + calendarRingWidth / 8f
                x2 = clockX(stippleTime.toDouble(), r2)
                y2 = clockY(stippleTime.toDouble(), r2)

                xmid = (x1 + x2) / 2f
                ymid = (y1 + y2) / 2f

                xlow = xmid - dxlow
                ylow = ymid - dylow
                xhigh = xmid - dxhigh
                yhigh = ymid - dyhigh

                // Path p = new Path();
                stipplePathCache!!.moveTo(x1, y1)
                stipplePathCache!!.lineTo(xlow, ylow)
                stipplePathCache!!.lineTo(x2, y2)
                stipplePathCache!!.lineTo(xhigh, yhigh)
                stipplePathCache!!.close()
                i++
                r1 = r2
                x1 = x2
                y1 = y2
                // canvas.drawPath(p, black);

                //                if(calendarTicker % 1000 == 0)
                //                    Log.v(TAG, "x1(" + Float.toString(x1) + "), y1(" + Float.toString(y1) +
                //                            "), x2(" + Float.toString(x1) + "), y2(" + Float.toString(y2) +
                //                            "), xlow(" + Float.toString(xlow) + "), ylow(" + Float.toString(ylow) +
                //                            "), xhigh(" + Float.toString(xhigh) + "), yhigh(" + Float.toString(yhigh) +
                //                            ")");
            }
        }
        canvas.drawPath(stipplePathCache, PaintCan[ambientLowBit, ambientMode, PaintCan.colorBlackFill])
    }

    private var batteryPathCache: Path? = null
    private var batteryCritical = false
    private var batteryCacheTime: Long = 0

    private fun drawBattery(canvas: Canvas) {
        val batteryWrapper = BatteryWrapper.getWrapper() ?: return // we're not ready yet, for whatever reason

        val time = TimeWrapper.gmtTime

        // we don't want to poll *too* often; this translates to about once per five minute
        if (batteryPathCache == null || time - batteryCacheTime > 300000) {
            Log.v(TAG, "fetching new battery status")
            batteryWrapper.fetchStatus()
            val batteryPct = batteryWrapper.batteryPct
            batteryCacheTime = time
            batteryPathCache = Path()

            //
            // New idea: draw nothing unless the battery is low. At 50%, we start a small yellow
            // circle. This scales in radius until it hits max size at 10%, then it switches to red.
            //

            Log.v(TAG, "battery at " + batteryPct)
            if (batteryPct > 0.5f) {
                // batteryPathCache = null;
            } else {
                val minRadius = 0.01f
                val maxRadius = 0.06f
                val dotRadius: Float
                if (batteryPct < 0.1)
                    dotRadius = maxRadius
                else
                    dotRadius = maxRadius - (maxRadius - minRadius) * (batteryPct - 0.1f) / 0.4f

                batteryPathCache!!.addCircle(cx.toFloat(), cy.toFloat(), radius * dotRadius, Path.Direction.CCW) // direction shouldn't matter

                batteryCritical = batteryPct <= 0.1f
                Log.v(TAG, "--> dot radius: $dotRadius, critical: $batteryCritical")
            }
        }

        // note that we'll flip the color from white to red once the battery gets below 10%
        // (in ambient mode, we can't show it at all because of burn-in issues)
        if (batteryPathCache != null) {
            val paint: Paint

            if (batteryCritical)
                paint = PaintCan[PaintCan.styleNormal, PaintCan.colorBatteryCritical]
            else
                paint = PaintCan[PaintCan.styleNormal, PaintCan.colorBatteryLow]

            if (!ambientMode)
                canvas.drawPath(batteryPathCache, paint)
        }
    }

    fun drawTimers(canvas: Canvas) {
        val currentTime = TimeWrapper.gmtTime // note that we're *not* using local time here

        val colorStopwatchSeconds = PaintCan[ambientLowBit, ambientMode, PaintCan.colorStopwatchSeconds]
        val colorStopwatchStroke = PaintCan[ambientLowBit, ambientMode, PaintCan.colorStopwatchStroke]
        val colorStopwatchFill = PaintCan[ambientLowBit, ambientMode, PaintCan.colorStopwatchFill]
        val colorTimerStroke = PaintCan[ambientLowBit, ambientMode, PaintCan.colorTimerStroke]
        val colorTimerFill = PaintCan[ambientLowBit, ambientMode, PaintCan.colorTimerFill]

        // Radius 0.9 is the start of the tick marks and the end of the normal minute hand. The normal
        // second hand goes to 0.95. If there's a stopwatch but no timer, then the stopwatch second and minute
        // hand will *both* go to 1.0 and the minute arc will be drawn from 0.9 to 1.0.

        // If there's a timer and no stopwatch, then the timer arc will be drawn from 0.9 to 1.0.

        // If there's a timer *and* a stopwatch active, then the timer wins with the outer ring and the
        // stopwatch has the inner ring. Outer ring is 0.95 to 1.0, inner ring is 0.9 to 0.95.

        // Note: if you see 0.94 or 0.96, that's really 0.95 but leaving buffer room for the overlapping
        // line widths. Likewise, if you see 0.99 rather than 1.0, same deal. All of these numbers have
        // been tweaked to try to get pixel-perfect results.

        // First, let's compute some stuff on the timer; if the timer is done but we haven't gotten
        // any updates from the app, then we should go ahead and treat it as if it's been reset
        var timerRemaining: Long // should go from 0 to timerDuration, where 0 means we're done
        if (!XWatchfaceReceiver.timerIsRunning) {
            timerRemaining = XWatchfaceReceiver.timerDuration - XWatchfaceReceiver.timerPauseElapsed
        } else {
            timerRemaining = XWatchfaceReceiver.timerDuration - currentTime + XWatchfaceReceiver.timerStart
        }
        if (timerRemaining < 0) timerRemaining = 0


        // we don't draw anything if the stopwatch is non-moving and at 00:00.00
        var stopwatchRenderTime: Long
        if (!XWatchfaceReceiver.stopwatchIsReset) {
            if (!XWatchfaceReceiver.stopwatchIsRunning) {
                stopwatchRenderTime = XWatchfaceReceiver.stopwatchBase
            } else {
                stopwatchRenderTime = currentTime - XWatchfaceReceiver.stopwatchStart + XWatchfaceReceiver.stopwatchBase
            }

            val seconds = stopwatchRenderTime / 1000.0f

            // rather than computing minutes directly (i.e., stopWatchRenderTime / 60000), we're instead going
            // to compute the integer number of hours (using Math.floor) and subtract that, giving us a resulting
            // number that ranges from [0-60).
            val hours = Math.floor((stopwatchRenderTime / 3600000f).toDouble()).toFloat()
            val minutes = stopwatchRenderTime / 60000.0f - hours * 60f

            val stopWatchR1 = 0.90f
            val stopWatchR2 = if (XWatchfaceReceiver.timerIsReset || timerRemaining == 0L) 0.995f else 0.945f

            // Stopwatch second hand only drawn if we're not in ambient mode.
            if (!ambientMode)
                drawRadialLine(canvas, seconds.toDouble(), 0.1f, 0.945f, colorStopwatchSeconds, null)

            // Stopwatch minute hand. Same thin gauge as second hand, but will be attached to the arc,
            // and thus look super cool.
            drawRadialLine(canvas, minutes.toDouble(), 0.1f, stopWatchR2 - 0.005f, colorStopwatchStroke, null)
            drawRadialArcFlatBottom(canvas, minutes, stopWatchR1, stopWatchR2, colorStopwatchFill, colorStopwatchStroke)
        }

        if (!XWatchfaceReceiver.timerIsReset && timerRemaining > 0) {
            if (!XWatchfaceReceiver.timerIsRunning) {
                timerRemaining = XWatchfaceReceiver.timerDuration - XWatchfaceReceiver.timerPauseElapsed
            } else {
                timerRemaining = XWatchfaceReceiver.timerDuration - currentTime + XWatchfaceReceiver.timerStart
            }
            if (timerRemaining < 0) timerRemaining = 0

            // timer hand will sweep counterclockwise from 12 o'clock back to 12 again when it's done
            val angle = timerRemaining.toFloat() / XWatchfaceReceiver.timerDuration.toFloat() * 60.toFloat()

            val timerR1 = if (XWatchfaceReceiver.stopwatchIsReset) 0.90f else 0.952f
            val timerR2 = 0.995f

            drawRadialLine(canvas, angle.toDouble(), 0.1f, timerR2 - 0.005f, colorTimerStroke, null)
            drawRadialArcFlatBottom(canvas, angle, timerR1, timerR2, colorTimerFill, colorTimerStroke)
        }
    }

    fun setSize(width: Int, height: Int) {
        Log.v(TAG, "setSize: $width x $height")
        cx = width / 2
        cy = height / 2
        savedCx = cx
        savedCy = cy

        if (cx == oldCx && cy == oldCy) return  // nothing changed, we're done

        oldCx = cx
        oldCy = cy

        radius = if (cx > cy) cy else cx // minimum of the two
        savedRadius = radius

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

    //    private float flatBottomCornerX1_R100;
    private var flatBottomCornerY1_R100: Float = 0.toFloat()
    //    private float flatBottomCornerX2_R100;
    //    private float flatBottomCornerY2_R100;
    //    private float flatBottomCornerX1_R80;
    private var flatBottomCornerY1_R80: Float = 0.toFloat()
    //    private float flatBottomCornerX2_R80;
    //    private float flatBottomCornerY2_R80;

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
            Log.v(TAG, "flatBottomCornerTime($flatBottomCornerTime) <-- angle($angle), missingBottomPixels($missingBottomPixels), cy($cy)")

            //            flatBottomCornerX1_R100 = clockX(flatBottomCornerTime, 1);
            flatBottomCornerY1_R100 = clockY(flatBottomCornerTime.toDouble(), 1f)
            //            flatBottomCornerX2_R100 = clockX(60 - flatBottomCornerTime, 1);
            //            flatBottomCornerY2_R100 = clockY(60 - flatBottomCornerTime, 1);

            //            flatBottomCornerX1_R80 = clockX(flatBottomCornerTime, 0.80f);
            flatBottomCornerY1_R80 = clockY(flatBottomCornerTime.toDouble(), 0.80f)
            //            flatBottomCornerX2_R80 = clockX(60 - flatBottomCornerTime, 0.80f);
            //            flatBottomCornerY2_R80 = clockY(60 - flatBottomCornerTime, 0.80f);
        } else {
            Log.v(TAG, "no flat bottom corrections")
        }
    }

    // given two values a1 and a2 associated with time parameters t1 and t2, find the
    // interpolated value for a given the time t
    //
    // example: a1 = 4, a2 = 10, t1=10, t2=30
    // interpolateT: t -> a
    //    10 -> 4
    //    20 -> 7
    //    30 -> 10
    //    50 -> 16  (we keep extrapolating on either end)
    private fun interpolate(a1: Float, t1: Float, a2: Float, t2: Float, t: Float): Float {
        val da = a2 - a1
        val dt = t2 - t1
        val ratio = (t - t1) / dt
        return a1 + da * ratio
    }

    private fun flatBottomX(time: Float, radius: Float): Float {
        // old, incorrect approach: linear interpolation between the sides
        //        float x1 = interpolate(flatBottomCornerX1_R80, 0.8f, flatBottomCornerX1_R100, 1f, radius);
        //        float x2 = interpolate(flatBottomCornerX2_R80, 0.8f, flatBottomCornerX2_R100, 1f, radius);
        //        return interpolate(x1, flatBottomCornerTime, x2, 60 - flatBottomCornerTime, time);

        // new shiny approach: project a line from the center, intersect it with a line, but
        // make the height of that line consistent with our pre-computed flatBottomCorner values,
        // otherwise we'd have the height of the intersection changing as the time got closer to 6 o'clock.

        val finalY = flatBottomY(time, radius)
        val r1X = clockX(time.toDouble(), 1f)
        val r1Y = clockY(time.toDouble(), 1f)

        return interpolate(r1X, r1Y, cx.toFloat(), cy.toFloat(), finalY)
    }

    private fun flatBottomY(time: Float, radius: Float): Float {
        // old, incorrect approach: linear interplation between the sides
        //        float y1 = interpolate(flatBottomCornerY1_R80, 0.8f, flatBottomCornerY1_R100, 1f, radius);
        //        float y2 = interpolate(flatBottomCornerY2_R80, 0.8f, flatBottomCornerY2_R100, 1f, radius);
        //        return interpolate(y1, flatBottomCornerTime, y2, 60 - flatBottomCornerTime, time);

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

    private var faceMode: Int = 0
    // nothing changed
    var ambientMode = false
        set(ambientMode) {
            Log.i(TAG, "Ambient mode: " + ambientMode)

            if (ambientMode == this.ambientMode) return

            this.ambientMode = ambientMode
            wipeCaches()
        }
    private var eventList: List<EventWrapper>? = null
    private var maxLevel: Int = 0


    // call this if you want this instance to head to the garbage collector; this disconnects
    // it from paying attention to changes in the ClockState
    fun kill() {
        clockState.deleteObserver(this)
    }

    private fun setupObserver() {
        clockState.addObserver(this)
    }

    // this gets called when the clockState updates itself
    override fun update(observable: Observable?, data: Any?) {
        Log.v(TAG, "update - start, instance($instanceID)")
        wipeCaches()
        TimeWrapper.update()
        this.faceMode = clockState.faceMode
        this.showDayDate = clockState.showDayDate
        this.showSeconds = clockState.showSeconds
        updateEventList()
        Log.v(TAG, "update - end")
    }

    private fun updateEventList() {
        // this is cheap enough that we can afford to do it at 60Hz
        this.maxLevel = clockState.maxLevel
        this.eventList = clockState.getVisibleEventList()
    }

    fun setBurnInProtection(burnInProtection: Boolean) {
        this.burnInProtection = burnInProtection || forceBurnInProtection
    }

    /**
     * Let us know if we're on a square or round watchface; we don't really care.
     */
    fun setRound(round: Boolean) {
    }

    companion object {
        private val TAG = "ClockFace"

        // for testing purposes, turn these things on; disable for production
        private val forceAmbientLowBit = false
        private val forceBurnInProtection = false
        private val forceMotoFlatBottom = false

        // for testing: sometimes it seems we have multiple instances of ClockFace, which is bad; let's
        // try to track them
        private var instanceCounter = 0


        // these ones are static so subsequent instances of this class can recover state; we start
        // them off non-zero, but they'll be changed quickly enough
        private var savedCx = 140
        private var savedCy = 140
        private var savedRadius = 140

        private val freqUpdate = 5f  // 5 Hz, or 0.20sec for second hand

        private val calendarRingMinRadius = 0.2f
        private val calendarRingMaxRadius = 0.9f
        private val calendarRingWidth = calendarRingMaxRadius - calendarRingMinRadius

        private val clipSeconds = false // force second hand to align with FPS boundaries (good for low-FPS drawing)

        private var debugMetricsPrinted = false

        private val NON_LINEAR_TABLE_SIZE = 1000
        private val NON_LINEAR_TABLE = DoubleArray(NON_LINEAR_TABLE_SIZE) { i ->
            // This is where we initialize our non-linear second hand table.
            // We're implementing y=[(1+sin(theta - pi/2))/2] ^ pow over x in [0,pi]
            // Adjusting the power makes the second hand hang out closer to its
            // starting position and then launch faster to hit the target when we
            // get closer to the next second.
            val thetaMinusPi2 = i * Math.PI / NON_LINEAR_TABLE_SIZE.toDouble() - Math.PI / 2.0

            // two components here: the non-linear part (the first line) and then a linear
            // part (the line below). This make sure we still have some motion. The second
            // hand never entirely stops.
            0.6 * Math.pow((1.0 + Math.sin(thetaMinusPi2)) / 2.0, 8.0)
            + 0.4 * (i.toDouble() / NON_LINEAR_TABLE_SIZE.toDouble())
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
