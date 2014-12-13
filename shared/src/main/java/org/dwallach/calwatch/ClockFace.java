/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ClockFace implements Observer {
    private static final String TAG = "ClockFace";
    //
    // A couple performance notes:
    //
    // We want to be able to render the watchface and calendar wedges at 60Hz. The naive thing to
    // do is a whole lot of trig, which has a very real performance impact at 60Hz. However, neither
    // the watch face nor the calendar wedges actually change very often.
    //
    // I initially tried rendering to a bitmap once and then blitting that bitmap at 60Hz, but even that
    // turns out to have a non-trivial performance load. The solution used here is modestly clever.
    // We render the calendar wedges and the time indices into a Path, and then render that path
    // at 60Hz. This means we do all the trig exactly once and the path (hopefully) is copied over
    // to the GPU, where it's small enough that it's going to avoid putting much if any load on anything
    // and should render stupid fast.
    //
    // Open question: whether this still holds true once we hit the wristwatch, where we're running
    // with a whole lot less horsepower than on the phone. Hopefully this will do the job. Also of
    // note: you can't render text to a path, only a canvas, so we can't cache the text
    // part if the user wants a watchface with text on it. Presumably, text rendering is otherwise
    // optimized and is *not our problem*.
    //

    private int cx, oldCx = -1;
    private int cy, oldCy = -1;
    private int radius;
    private float shadow;

    private boolean showSeconds = true, showDayDate = true;
    private boolean ambientLowBit = false;
    private boolean muteMode = false;

    private static final float freqUpdate = 5;  // 5 Hz, or 0.20sec for second hand

    private static float calendarRingMinRadius = 0.2f;
    private static float calendarRingMaxRadius = 0.9f;
    private static float calendarRingWidth = calendarRingMaxRadius - calendarRingMinRadius;

    private boolean clipSeconds = false; // force second hand to align with FPS boundaries (good for low-FPS drawing)

    private int missingBottomPixels = 0; // Moto 360 hack; set to non-zero number to pull up the indicia

    private Paint white, whiteHour, whiteMinute, yellow, smWhite, smYellow, black, smBlack, smRed, gray, outlineBlack, thickOutlineBlack, superThinBlack, smTextShadow, textShadow;

    private ClockState clockState;

    // set for Moto 360
    public void setMissingBottomPixels(int missingBottomPixels) {
        this.missingBottomPixels = missingBottomPixels;
    }

    private Paint newPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.HINTING_ON);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);

        return p;
    }

    /**
     * Tell the clock face if we're in "mute" mode. Unclear we want to actually do anything different
     * @param muteMode
     */
    public void setMuteMode(boolean muteMode) {
        this.muteMode = muteMode;
    }

    /**
     * If true, ambient redrawing will be purely black and white, without any anti-aliasing (default: off)
     * @param ambientLowBit
     */
    public void setAmbientLowBit(boolean ambientLowBit) {
        Log.v(TAG, "ambient low bit: " + ambientLowBit)
        this.ambientLowBit = ambientLowBit;
    }

    public ClockFace() {
        Log.v(TAG, "ClockFace setup!");

        this.clockState = ClockState.getSingleton();
        setupObserver();
        update(null, null); // initialize variables from initial constants, or whatever else is hanging out in ClockState

        white = newPaint();
        whiteHour = newPaint();
        whiteMinute = newPaint();
        yellow = newPaint();
        smWhite = newPaint();
        smYellow = newPaint();
        black = newPaint();
        smBlack = newPaint();
        smRed = newPaint();
        gray = newPaint();
        outlineBlack = newPaint();
        thickOutlineBlack = newPaint();
        smTextShadow = newPaint();
        textShadow = newPaint();
        superThinBlack = newPaint();

        yellow.setColor(Color.YELLOW);
        smYellow.setColor(Color.YELLOW);
        black.setColor(Color.BLACK);
        smBlack.setColor(Color.BLACK);
        outlineBlack.setColor(Color.BLACK);
        thickOutlineBlack.setColor(Color.BLACK);
        smTextShadow.setColor(Color.BLACK);
        textShadow.setColor(Color.BLACK);
        superThinBlack.setColor(Color.BLACK);
        smRed.setColor(Color.RED);
        gray.setColor(Color.GRAY);

        smYellow.setTextAlign(Paint.Align.LEFT);
        smWhite.setTextAlign(Paint.Align.LEFT);
        smTextShadow.setTextAlign(Paint.Align.LEFT);
        smBlack.setTextAlign(Paint.Align.LEFT);
        white.setTextAlign(Paint.Align.CENTER);
        whiteHour.setTextAlign(Paint.Align.CENTER);
        whiteMinute.setTextAlign(Paint.Align.CENTER);
        black.setTextAlign(Paint.Align.CENTER);

        outlineBlack.setStyle(Paint.Style.STROKE);
        thickOutlineBlack.setStyle(Paint.Style.STROKE);
        smTextShadow.setStyle(Paint.Style.STROKE);
        textShadow.setStyle(Paint.Style.STROKE);
        superThinBlack.setStyle(Paint.Style.STROKE);

        // Now, to detect a Moto 360 and install the hack. FYI, here's what all the Build.MODEL strings
        // are, at least on my own Moto 360:

        // BOARD: minnow
        // BRAND: motorola
        // DEVICE: minnow
        // HARDWARE: minnow
        // ID: KGW42R
        // MANUFACTURER: Motorola
        // MODEL: Moto 360
        // PRODUCT: metallica
        // TYPE: user

        if(Build.MODEL.contains("Moto 360") || Build.PRODUCT.contains("metallica")) {
            Log.v(TAG, "Moto 360 detected. Flat bottom hack enabled.");
            setMissingBottomPixels(30);
        }
    }

    /*
     * the expectation is that you call this method *not* from the UI thread but instead
     * from a helper thread, elsewhere
     */
    public void drawEverything(Canvas canvas) {
        TimeWrapper.frameStart();

        // draw the calendar wedges first, at the bottom of the stack, then the face indices
        drawCalendar(canvas);
        drawFace(canvas);
        drawHands(canvas);

        // something a real watch can't do: float the text over the hands
        // this visually conflicts with other notifications, drawn as text above the hands,
        // so it's easiest to just cut it during ambient mode
        if(!getAmbientMode() && showDayDate) drawMonthBox(canvas);

        // and lastly, the battery meter
        // -- note that the watch draws its own battery meter, so this is really just window
        //    dressing, unnecessary in ambient mode
        if(!getAmbientMode()) drawBattery(canvas);


        TimeWrapper.frameEnd();
    }

    private void drawRadialLine(Canvas canvas, double seconds, float startRadius, float endRadius, Paint paint, Paint shadowPaint) {
        drawRadialLine(canvas, seconds, startRadius, endRadius, paint, shadowPaint, false);
    }

    private void drawRadialLine(Canvas canvas, double seconds, float startRadius, float endRadius, Paint paint, Paint shadowPaint, boolean forceVertical) {
        Path p = new Path();
        drawRadialLine(p, paint.getStrokeWidth(), seconds, startRadius, endRadius, forceVertical, false);
        canvas.drawPath(p, paint);
        canvas.drawPath(p, shadowPaint);
    }
    private void drawRadialLine(Path path, float strokeWidth, double seconds, float startRadius, float endRadius, boolean forceVertical, boolean flatBottomHack) {
        float x1, x2, y1, y2;

        if(flatBottomHack) {
            float clipRadius = radiusToEdge(seconds);
            if(endRadius > clipRadius) {
                float dr = endRadius - clipRadius;
                startRadius -= dr;
                endRadius -= dr;
            }
        }

        x1 = clockX(seconds, startRadius);
        y1 = clockY(seconds, startRadius);
        x2 = clockX(seconds, endRadius);
        y2 = clockY(seconds, endRadius);
        if(forceVertical) {
            seconds = 0;
            x2 = x1;
        }

        float dx = (clockX(seconds + 15, 1f) - cx) * 0.5f * strokeWidth  / radius;
        float dy = (clockY(seconds + 15, 1f) - cy) * 0.5f * strokeWidth / radius;

        path.moveTo(x1+dx, y1+dy);
        path.lineTo(x2+dx, y2+dy);
        path.lineTo(x2 - dx, y2 - dy);
        path.lineTo(x1-dx, y1-dy);
        // path.lineTo(x1+dx, y1+dy);
        path.close();
    }

    private RectF getRectRadius(float radius) {
        return new RectF(
                clockX(45,radius), // left
                clockY(0,radius),  // top
                clockX(15,radius), // right
                clockY(30,radius));// bottom
    }

    private void drawRadialArc(Canvas canvas, PathCache pc, double secondsStart, double secondsEnd, float startRadius, float endRadius, Paint paint, Paint outlinePaint) {
        /*
         * Below is an attempt to do this "correctly" using the arc functionality supported natively
         * by Android's Path.
         */

        if(startRadius < 0 || startRadius > 1 || endRadius < 0 || endRadius > 1) {
            Log.e(TAG, "arc too big! radius(" + Float.toString((float) startRadius) + "," + Float.toString((float) endRadius) +
                            "), seconds(" + Float.toString((float) secondsStart) + "," + Float.toString((float) secondsEnd) + ")");
        }

        Path p = pc.get();
        if(p == null) {
            p = new Path();

            RectF midOval = getRectRadius((startRadius + endRadius) / 2f + 0.025f);
            RectF midOvalDelta = getRectRadius((startRadius + endRadius) / 2f - 0.025f);
            RectF startOval = getRectRadius(startRadius);
            RectF endOval = getRectRadius(endRadius);
            if(getAmbientMode()) {
                // in ambient mode, we're going to draw some slender arcs of fixed width at roughly the center of the big
                // colored pie wedge which we normally show when we're not in ambient mode
                p.arcTo(midOval, (float) (secondsStart * 6 - 90), (float) ((secondsEnd - secondsStart) * 6), true);
                p.arcTo(midOvalDelta, (float) (secondsEnd * 6 - 90), (float) (-(secondsEnd - secondsStart) * 6));
                p.close();
            } else {
                p.arcTo(startOval, (float) (secondsStart * 6 - 90), (float) ((secondsEnd - secondsStart) * 6), true);
                p.arcTo(endOval, (float) (secondsEnd * 6 - 90), (float) (-(secondsEnd - secondsStart) * 6));
                p.close();

//            Log.e(TAG, "New arc: radius(" + Float.toString((float) startRadius) + "," + Float.toString((float) endRadius) +
//                    "), seconds(" + Float.toString((float) secondsStart) + "," + Float.toString((float) secondsEnd) + ")");
//            Log.e(TAG, "--> arcTo: startOval, " + Float.toString((float) (secondsStart * 6 - 90)) + ", " +  Float.toString((float) ((secondsEnd - secondsStart) * 6)));
//            Log.e(TAG, "--> arcTo: endOval, " + Float.toString((float) (secondsEnd * 6 - 90)) + ", " +  Float.toString((float) (-(secondsEnd - secondsStart) * 6)));
            }

            pc.set(p);
        }

        canvas.drawPath(p, paint);
        canvas.drawPath(p, outlinePaint);
    }

    private void drawMonthBox(Canvas canvas) {
        // for now, hard-coded to the 9-oclock position
        String m = TimeWrapper.localMonthDay();
        String d = TimeWrapper.localDayOfWeek();
        float x1, y1;
        x1 = clockX(45, .85f);
        y1 = clockY(45, .85f);

//        Paint paint = (ambientMode)? smWhite : smYellow;
        Paint paint = smWhite; // yellow just doesn't look right, so we'll keep it monochromatic

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float dybottom = -metrics.ascent-metrics.leading; // smidge it up a bunch
        float dytop = -metrics.descent; // smidge it down a little

        drawShadowText(canvas, d, x1, y1+dybottom, paint, smTextShadow);
        drawShadowText(canvas, m, x1, y1+dytop, paint, smTextShadow);
    }

    private void drawShadowText(Canvas canvas, String text, float x, float y, Paint paint, Paint shadowPaint) {
        canvas.drawText(text, x, y, shadowPaint);
        canvas.drawText(text, x, y, paint);
    }

    static private boolean debugMetricsPrinted = false;
    private volatile Path facePathCache = null;
    private volatile int facePathCacheMode = -1;

    private void drawFace(Canvas canvas) {
        Path p = facePathCache; // make a local copy, avoid concurrency crap
        // draw thin lines (indices)

        boolean bottomHack = (missingBottomPixels > 0);

        // check if we've already rendered the face
        if(faceMode != facePathCacheMode || p == null) {
            p = new Path();
            Log.v(TAG, "rendering new face, faceMode(" + faceMode + ")");

            if (faceMode == ClockState.FACE_TOOL)
                for (int i = 1; i < 60; i++)
                    if(i%5 != 0)
                        drawRadialLine(p, smWhite.getStrokeWidth(), i, .9f, 1.0f, false, bottomHack);

            float strokeWidth;

            if (faceMode == ClockState.FACE_LITE || faceMode == ClockState.FACE_NUMBERS)
                strokeWidth = smWhite.getStrokeWidth();
            else
                strokeWidth = white.getStrokeWidth();


            for (int i = 0; i < 60; i += 5) {
                if (i == 0) { // top of watch: special
                    if (faceMode != ClockState.FACE_NUMBERS) {
                        drawRadialLine(p, strokeWidth, -0.4f, .8f, 1.0f, true, false);
                        drawRadialLine(p, strokeWidth, 0.4f, .8f, 1.0f, true, false);
                    }
                } else if (i == 45 && !getAmbientMode() && showDayDate) { // 9 o'clock, don't extend into the inside
                    drawRadialLine(p, strokeWidth, i, 0.9f, 1.0f, false, false);
                } else {
                    // we want lines for 1, 2, 4, 5, 7, 8, 10, and 11 no matter what
                    if (faceMode != ClockState.FACE_NUMBERS || !(i == 15 || i == 30 || i == 45)) {
                        // in the particular case of 6 o'clock and the Moto 360 bottomHack, we're
                        // going to make the 6 o'clock index line the same length as the other lines
                        // so it doesn't stand out as much
                        if (i == 30 && bottomHack)
                            drawRadialLine(p, strokeWidth, i, .9f, 1.0f, false, bottomHack);
                        else
                            drawRadialLine(p, strokeWidth, i, .8f, 1.0f, false, bottomHack);
                    }
                }
            }

            facePathCache = p;
            facePathCacheMode = faceMode;
        }

        // concurrency note: even if some other thread sets the facePathCache to null,
        // the local copy we allocated here will survive for the drawPath we're about to do
        canvas.drawPath(p, smWhite);
        canvas.drawPath(p, superThinBlack);

        if(faceMode == ClockState.FACE_NUMBERS) {
            // in this case, we'll draw "12", "3", and "6". No "9" because that's where the
            // month and day will go
            float x, y, r;

            //
            // note: metrics.ascent is a *negative* number while metrics.descent is a *positive* number
            //
            Paint.FontMetrics metrics = white.getFontMetrics();


            //
            // 12 o'clock
            //
            r = 0.9f;

            x = clockX(0, r);
            y = clockY(0, r) - metrics.ascent / 1.5f;

            white.setTextAlign(Paint.Align.CENTER);
            black.setTextAlign(Paint.Align.CENTER);
            drawShadowText(canvas, "12", x, y, white, textShadow);

            if(!debugMetricsPrinted) {
                debugMetricsPrinted = true;
                Log.v(TAG, "x(" + Float.toString(x) + "), y(" + Float.toString(y) + "), metrics.descent(" + Float.toString(metrics.descent) + "), metrics.ascent(" + Float.toString(metrics.ascent) + ")");
            }

            //
            // 3 o'clock
            //

            r = 0.9f;
            float threeWidth = white.measureText("3");

            x = clockX(15, r) - threeWidth / 2f;
            y = clockY(15, r) - metrics.ascent / 2f - metrics.descent / 2f; // empirically gets the middle of the "3" -- actually a smidge off with Roboto but close enough for now and totally font-dependent with no help from metrics

            drawShadowText(canvas, "3", x, y, white, textShadow);

            //
            // 6 o'clock
            //

            r = 0.9f;

            x = clockX(30, r);
            if(missingBottomPixels != 0)
                y = clockY(30, r) + metrics.descent - (missingBottomPixels); // another hack for Moto 360
            else
                y = clockY(30, r) + (0.75f * metrics.descent); // scoot it up a tiny bit

            drawShadowText(canvas, "6", x, y, white, textShadow);

            //
            // 9 o'clock
            //

            if(getAmbientMode() || !showDayDate) {
                r = 0.9f;
                float nineWidth = white.measureText("9");

                x = clockX(45, r) + nineWidth / 2f;
                y = clockY(45, r) - metrics.ascent / 2f - metrics.descent / 2f;

                drawShadowText(canvas, "9", x, y, white, textShadow);


            }
        }
    }

    private void drawHands(Canvas canvas) {
        long time = TimeWrapper.getLocalTime();

        double seconds = time / 1000.0;
        double minutes = seconds / 60.0;
        double hours = minutes / 12.0;  // because drawRadialLine is scaled to a 60-unit circle

        drawRadialLine(canvas, hours, 0.1f, 0.6f, whiteHour, superThinBlack);
        drawRadialLine(canvas, minutes, 0.1f, 0.9f, whiteMinute, superThinBlack);

        if(!getAmbientMode() && showSeconds) {
            // ugly details: we might run 10% or more away from our targets at 4Hz, making the second
            // hand miss the indices. Ugly. Thus, some hackery.
            if(clipSeconds) seconds = Math.floor(seconds * freqUpdate) / freqUpdate;
            drawRadialLine(canvas, seconds, 0.1f, 0.95f, smRed, superThinBlack);
        }
    }

    /**
     * call this if external forces at play may have invalidated state
     * being saved inside ClockFace
     */
    public void wipeCaches() {
        Log.v(TAG, "clearing caches");

        facePathCache = null;
        batteryPathCache = null;
        stipplePathCache = null;
        stippleTimeCache = -1;

        if(eventList != null)
            for(EventWrapper eventWrapper: eventList) {
                PathCache pc = eventWrapper.getPathCache();
                if(pc != null) pc.set(null);
            }
    }

    private static int calendarTicker = 0;
    private long stippleTimeCache = -1;
    private Path stipplePathCache = null;

    private void drawCalendar(Canvas canvas) {
        calendarTicker++;

        // this line represents a big change; we're still an observer of the clock state, but now
        // we're also polling it; it promises to support this polling efficiently, and in return,
        // we know we've always got an up to date set of calendar wedges
        updateEventList();

        if(eventList == null) {
            if (calendarTicker % 1000 == 0) Log.v(TAG, "drawCalendar starting, eventList is null");
            update(null, null); // probably won't accomplish any more than the updateEventList above...

            if(eventList == null) {
                Log.v(TAG, "eventList still null after update; giving up");
                return; // again, must not be ready yet
            }
        }

        long time = TimeWrapper.getLocalTime();

        for(EventWrapper eventWrapper: eventList) {
            double arcStart, arcEnd;
            WireEvent e = eventWrapper.getWireEvent();
            int evMinLevel = eventWrapper.getMinLevel();
            int evMaxLevel = eventWrapper.getMaxLevel();

            long startTime = e.startTime;
            long endTime = e.endTime;

            arcStart = startTime / 720000.0;
            arcEnd = endTime / 720000.0;

            // path caching happens inside drawRadialArc
            drawRadialArc(canvas, eventWrapper.getPathCache(), arcStart, arcEnd,
                    calendarRingMaxRadius - evMinLevel * calendarRingWidth / (maxLevel + 1),
                    calendarRingMaxRadius - (evMaxLevel + 1) * calendarRingWidth / (maxLevel + 1),
                    (getAmbientMode())?white:eventWrapper.getPaint(),
                    outlineBlack);
        }

        // Lastly, draw a stippled pattern at the current hour mark to delineate where the
        // twelve-hour calendar rendering zone starts and ends.


        // integer division gets us the exact hour, then multiply by 5 to scale to our
        // 60-second circle
        long stippleTime = (time) / (1000 * 60 * 60);
        stippleTime *= 5;

        // we might want to rejigger this to be paranoid about concurrency smashing stipplePathCache,
        // but it's less of a problem here than with the watchFace, because the external UI isn't
        // inducing the state here to change
        if(stippleTime != stippleTimeCache || stipplePathCache == null) {
            stipplePathCache = new Path();
            stippleTimeCache = stippleTime;

//            if(calendarTicker % 1000 == 0)
//                Log.v(TAG, "StippleTime(" + stippleTime +
//                        "),  currentTime(" + Float.toString((time) / 720000f) + ")");

            float r1=calendarRingMinRadius, r2;

            // eight little diamonds -- precompute the deltas when we're all the way out at the end,
            // then apply elsewhere

            float dxlow, dylow, dxhigh, dyhigh;
            float x1, y1, x2, y2, xlow, ylow, xmid, ymid, xhigh, yhigh;
            final float stippleWidth = 0.3f;
            final int stippleSteps = 8;
            final float rDelta = calendarRingWidth/(float)stippleSteps;

            x1 = clockX(stippleTime, calendarRingMaxRadius);
            y1 = clockY(stippleTime, calendarRingMaxRadius);
            x2 = clockX(stippleTime, calendarRingMaxRadius - rDelta);
            y2 = clockY(stippleTime, calendarRingMaxRadius - rDelta);
            xmid = (x1 + x2) / 2f;
            ymid = (y1 + y2) / 2f;
            xlow = clockX(stippleTime - stippleWidth, calendarRingMaxRadius - rDelta/2);
            ylow = clockY(stippleTime - stippleWidth, calendarRingMaxRadius - rDelta/2);
            xhigh = clockX(stippleTime + stippleWidth, calendarRingMaxRadius - rDelta/2);
            yhigh = clockY(stippleTime + stippleWidth, calendarRingMaxRadius - rDelta/2);
            dxlow = xmid - xlow;
            dylow = ymid - ylow;
            dxhigh = xmid - xhigh;
            dyhigh = ymid - yhigh;

            r1 = calendarRingMinRadius;
            x1 = clockX(stippleTime, r1);
            y1 = clockY(stippleTime, r1);
            for(int i=0; i<8; i++, r1=r2, x1=x2, y1=y2) {
                r2 = r1 + calendarRingWidth / 8f;
                x2 = clockX(stippleTime, r2);
                y2 = clockY(stippleTime, r2);

                xmid = (x1 + x2) / 2f;
                ymid = (y1 + y2) / 2f;

                xlow = xmid - dxlow;
                ylow = ymid - dylow;
                xhigh = xmid - dxhigh;
                yhigh = ymid - dyhigh;

                // Path p = new Path();
                stipplePathCache.moveTo(x1, y1);
                stipplePathCache.lineTo(xlow, ylow);
                stipplePathCache.lineTo(x2, y2);
                stipplePathCache.lineTo(xhigh, yhigh);
                stipplePathCache.close();
                // canvas.drawPath(p, black);

//                if(calendarTicker % 1000 == 0)
//                    Log.v(TAG, "x1(" + Float.toString(x1) + "), y1(" + Float.toString(y1) +
//                            "), x2(" + Float.toString(x1) + "), y2(" + Float.toString(y2) +
//                            "), xlow(" + Float.toString(xlow) + "), ylow(" + Float.toString(ylow) +
//                            "), xhigh(" + Float.toString(xhigh) + "), yhigh(" + Float.toString(yhigh) +
//                            ")");
            }
        }
        canvas.drawPath(stipplePathCache, black);
    }

    private Path batteryPathCache = null;
    private boolean batteryCritical = false;

    private void drawBattery(Canvas canvas) {
        BatteryWrapper batteryWrapper = BatteryWrapper.getSingleton();
        float batteryPct;

        if(batteryWrapper == null) {
            // Whoops, not initialized yet, so we'll just return and wait for it to come
            // around later. We're initializing this from the service *and* from the activity,
            // so it *should* be around, but paranoia says check your errors...
            return;
        }

        // we don't want to poll *too* often; this translates to about once per five minute
        if(batteryPathCache == null ||
                (calendarTicker % 300000 == 0)) {
            batteryWrapper.fetchStatus();
            Log.v(TAG, "fetching new battery status (" + calendarTicker + ")");
            batteryPct = batteryWrapper.getBatteryPct();
            batteryPathCache = new Path();

            //
            // New idea: draw nothing unless the battery is low. At 50%, we start a small yellow
            // circle. This scales in radius until it hits max size at 10%, then it switches to red.
            //

            Log.v(TAG, "battery at " + batteryPct);
            if(batteryPct > 0.5f) {
                // batteryPathCache = null;
            } else {
                float minRadius = 0.02f, maxRadius = 0.06f;
                float dotRadius;
                if(batteryPct < 0.1)
                    dotRadius = maxRadius;
                else
                    dotRadius = maxRadius - ((maxRadius - minRadius) * (batteryPct - 0.1f) / 0.4f);

                Log.v(TAG, "--> dot radius: " + dotRadius);
                batteryPathCache.addCircle(cx, cy, radius * dotRadius, Path.Direction.CCW); // direction shouldn't matter

                batteryCritical = batteryPct <= 0.1f;
            }
        }

        // note that we'll flip the color from white to red once the battery gets below 10%
        // TODO except if we're in ambient mode?
        if(batteryPathCache != null) {
            Paint paint;

            if(batteryCritical)
                paint = smRed;
            else
                paint = smYellow;

            if(getAmbientMode())
                paint = smWhite;

            canvas.drawPath(batteryPathCache, paint);
        }
    }

    public void setAmbientMode(boolean ambientMode) {
        Log.i(TAG, "Ambient mode: " + ambientMode);

        if(ambientMode == this.ambientMode) return; // nothing changed

        this.ambientMode = ambientMode;
        wipeCaches();
    }

    public boolean getAmbientMode() {
        return ambientMode;
    }

    public void setSize(int width, int height) {
        cx = width / 2;
        cy = height / 2;

        if(cx == oldCx && cy == oldCy) return; // nothing changed, we're done

        oldCx = cx;
        oldCy = cy;

        radius = (cx > cy) ? cy : cx; // minimum of the two
        float textSize = radius / 3f;
        float smTextSize = radius / 6f;
        float lineWidth = radius / 20f;

        shadow = lineWidth / 20f;  // for drop shadows

        white.setTextSize(textSize);
        whiteHour.setTextSize(textSize);
        whiteMinute.setTextSize(textSize);
        yellow.setTextSize(textSize);
        gray.setTextSize(textSize);
        black.setTextSize(textSize);
        textShadow.setTextSize(textSize);

        white.setStrokeWidth(lineWidth);
        whiteHour.setStrokeWidth(lineWidth * 1.5f);
        whiteMinute.setStrokeWidth(lineWidth);
        yellow.setStrokeWidth(lineWidth);
        gray.setStrokeWidth(lineWidth);
        black.setStrokeWidth(lineWidth);

        smWhite.setTextSize(smTextSize);
        smYellow.setTextSize(smTextSize);
        smBlack.setTextSize(smTextSize);
        smTextShadow.setTextSize(smTextSize);

        smWhite.setStrokeWidth(lineWidth /3);
        smYellow.setStrokeWidth(lineWidth /3);
        smBlack.setStrokeWidth(lineWidth /4);
        smRed.setStrokeWidth(lineWidth /3);

        smTextShadow.setStrokeWidth(lineWidth / 4);
        textShadow.setStrokeWidth(lineWidth / 2);
        outlineBlack.setStrokeWidth(lineWidth /6);
        thickOutlineBlack.setStrokeWidth(lineWidth*1.2f);
        superThinBlack.setStrokeWidth(lineWidth / 8);

        wipeCaches();
    }

    // clock math
    private float clockX(double seconds, float fractionFromCenter) {
        double angleRadians = ((seconds - 15) * Math.PI * 2f) / 60.0;
        return (float)(cx + radius * fractionFromCenter * Math.cos(angleRadians));
    }

    private float clockY(double seconds, float fractionFromCenter) {
        double angleRadians = ((seconds - 15) * Math.PI * 2f) / 60.0;
        return (float)(cy + radius * fractionFromCenter * Math.sin(angleRadians));
    }

    // hack for Moto360: given the location on the dial (seconds), and the originally
    // desired radius, this returns your new radius that will touch the flat bottom
    private float radiusToEdge(double seconds) {
        float yOrig = clockY(seconds, 1f);
        if(yOrig > cy*2 - missingBottomPixels) {
            // given:
            //   yOrig = cy + radius * fractionFromCenter * sin(angle)
            // substitute the desired Y, i.e.,
            //   cy*2 - missingBottomPixels = cy + radius * fractionFromCenter * sin(angle)
            // and now solve for fractionFromCenter:
            //   (cy - missingBottomPixels) / (radius * sin(angle)) = fractionFromCenter
            double angleRadians = ((seconds - 15) * Math.PI * 2f) / 60.0;
            try {
                float newRadius = (float) ((cy - missingBottomPixels) / (radius * Math.sin(angleRadians)));
                return newRadius;
            } catch (ArithmeticException e) {
                // division by zero, weird, so fall back to the default
                return 1f;
            }
        } else
            return 1f;
    }

    private int faceMode;
    private boolean ambientMode = false;
    private List<EventWrapper> eventList;
    private int maxLevel;


    // call this if you want this instance to head to the garbage collector; this disconnects
    // it from paying attention to changes in the ClockState
    public void destroy() {
        clockState.deleteObserver(this);
    }

    private void setupObserver() {
        clockState.addObserver(this);
    }

    // this gets called when the clockState updates itself
    @Override
    public void update(Observable observable, Object data) {
        wipeCaches();
        TimeWrapper.update();
        this.faceMode = clockState.getFaceMode();
        this.showDayDate = clockState.getShowDayDate();
        this.showSeconds = clockState.getShowSeconds();
        updateEventList();
    }

    private void updateEventList() {
        // this is cheap enough that we can afford to do it at 60Hz
        this.maxLevel = clockState.getMaxLevel();
        this.eventList = clockState.getVisibleLocalEventList();
    }
}
