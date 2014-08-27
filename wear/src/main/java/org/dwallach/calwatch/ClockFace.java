package org.dwallach.calwatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.format.DateUtils;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ClockFace {
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
    // optimized and is *not my problem*.
    //

    private int cx;
    private int cy;
    private int radius;
    private float shadow;
    private boolean showSeconds = true;
    private static final float freqUpdate = 5;  // 5 Hz, or 0.20sec for second hand

    private static float calendarRingMinRadius = 0.2f;
    private static float calendarRingMaxRadius = 0.9f;
    private static float calendarRingWidth = calendarRingMaxRadius - calendarRingMinRadius;

    private boolean clipSeconds = false; // force second hand to align with FPS boundaries (good for low-FPS drawing)

    private Paint white, yellow, smWhite, smYellow, black, smBlack, smRed, gray, outlineBlack, superThinBlack;

    Context context;

    public final static int FACE_TOOL = 0;
    public final static int FACE_NUMBERS = 1;
    public final static int FACE_LITE = 2;

    private volatile int faceMode = FACE_TOOL;

    public void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;
        this.facePathCache = null;
    }

    public int getFaceMode() {
        return faceMode;
    }

    private Paint newPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.HINTING_ON);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);

        return p;
    }

    public ClockFace(Context context) {
        this.context = context;

        Log.v("ClockFace", "ClockFace setup!");
        white = newPaint();
        yellow = newPaint();
        smWhite = newPaint();
        smYellow = newPaint();
        black = newPaint();
        smBlack = newPaint();
        smRed = newPaint();
        gray = newPaint();
        outlineBlack = newPaint();
        superThinBlack = newPaint();

        yellow.setColor(Color.YELLOW);
        smYellow.setColor(Color.YELLOW);
        black.setColor(Color.BLACK);
        smBlack.setColor(Color.BLACK);
        outlineBlack.setColor(Color.BLACK);
        superThinBlack.setColor(Color.BLACK);
        smRed.setColor(Color.RED);
        gray.setColor(Color.GRAY);

        smYellow.setTextAlign(Paint.Align.LEFT);
        smWhite.setTextAlign(Paint.Align.LEFT);
        smBlack.setTextAlign(Paint.Align.LEFT);
        white.setTextAlign(Paint.Align.CENTER);
        black.setTextAlign(Paint.Align.CENTER);

        outlineBlack.setStyle(Paint.Style.STROKE);
        superThinBlack.setStyle(Paint.Style.STROKE);
    }

    public void setShowSeconds(boolean showSeconds) {
        this.showSeconds = showSeconds;
    }

    /*
     * sets whether the second hand should be aligned properly (only use in low FPS situations)
     */
    public void setClipSeconds(boolean clipSeconds) {
        this.clipSeconds = clipSeconds;
    }

    public boolean getShowSeconds() {
        return showSeconds;
    }

    /*
     * the expectation is that you call this method *not* from the UI thread but instead
     * from a helper thread, elsewhere
     */
    public void drawEverything(Context context, Canvas canvas) {
        // background?

        // canvas.drawCircle(cx, cy, radius, gray);
        // canvas.drawCircle(cx, cy, radius * .95f, black);

        // draw the calendar wedges first, at the bottom of the stack, then the face indices
        drawCalendar(context, canvas);
        drawFace(context, canvas);

        // something a real watch can't do: float the text over the hands
        drawHands(context, canvas);
        drawMonthBox(context, canvas);
    }

    public void drawRadialLine(Canvas canvas, double seconds, float startRadius, float endRadius, Paint paint, Paint shadowPaint) {
        drawRadialLine(canvas, seconds, startRadius, endRadius, paint, shadowPaint, false);
    }

    public void drawRadialLine(Canvas canvas, double seconds, float startRadius, float endRadius, Paint paint, Paint shadowPaint, boolean forceVertical) {
        Path p = new Path();
        drawRadialLine(p, paint.getStrokeWidth(), seconds, startRadius, endRadius, forceVertical);
        canvas.drawPath(p, paint);
        canvas.drawPath(p, shadowPaint);
    }
    public void drawRadialLine(Path path, float strokeWidth, double seconds, float startRadius, float endRadius, boolean forceVertical) {
        float x1, x2, y1, y2;

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
        path.lineTo(x2-dx, y2-dy);
        path.lineTo(x1-dx, y1-dy);
        // path.lineTo(x1+dx, y1+dy);
        path.close();
    }

    public void drawRadialArc(Canvas canvas, PathCache pc, double secondsStart, double secondsEnd, float startRadius, float endRadius, Paint paint, Paint outlinePaint) {
        /*
         * Below is an attempt to do this "correctly" using the arc functionality supported natively
         * by Android's Path. This implementation totally didn't work. Rather than debugging it,
         * we instead did the dumb-but-accurate thing of stepping at a very small angle and computing
         * lots of points along the arc. This sounds criminal from a performance perspective, but all
         * of it is dumped into a path where the GPU can rendering it directly, so all the trig computations
         * happen only once. (Or at least, only once per refresh of the calendar, which doesn't happen
         * all that frequently.)

        Path p = new Path();
        RectF startOval = new RectF(
                clockX(45,startRadius), // bottom
                clockY(30,startRadius), // left
                clockY(15,startRadius), // right
                clockY(0,startRadius)); // top
        RectF endOval = new RectF(
                clockX(45,endRadius), // bottom
                clockY(30,endRadius), // left
                clockY(15,endRadius), // right
                clockY(0,endRadius)); // top

        p.addArc(startOval, (float)(secondsStart * Math.PI / 30.0 + Math.PI / 2.0), (float)((secondsEnd - secondsStart) * Math.PI / 30.0 + Math.PI / 2.0));
        p.lineTo(clockX(secondsEnd,endRadius), clockY(secondsEnd, endRadius));
        p.arcTo(endOval, (float)(secondsEnd * Math.PI / 30.0 + Math.PI / 2.0), (float)(-(secondsEnd - secondsStart) * Math.PI / 30.0 + Math.PI / 2.0));
        p.lineTo(clockX(secondsStart,startRadius), clockY(secondsStart, startRadius));
        */

        /*
         * Caching the path in the event gives us good performance, despite the delta theta
         * being awfully small and thus a whole lot of trig going on in here.
         */
        Path p = pc.get();
        if(p == null) {
            p = new Path();
            double dt = 0.2; // smaller numbers == closer to a proper arc

            p.moveTo(clockX(secondsStart, startRadius), clockY(secondsStart, startRadius));
            for (double theta = secondsStart; theta < secondsEnd; theta += dt)
                p.lineTo(clockX(theta, startRadius), clockY(theta, startRadius));
            p.lineTo(clockX(secondsEnd, startRadius), clockY(secondsEnd, startRadius));
            p.lineTo(clockX(secondsEnd, endRadius), clockY(secondsEnd, endRadius));
            for (double theta = secondsEnd; theta >= secondsStart; theta -= dt)
                p.lineTo(clockX(theta, endRadius), clockY(theta, endRadius));
            p.close();

            pc.set(p);
        }

        canvas.drawPath(p, paint);
        canvas.drawPath(p, outlinePaint);
    }

    public void drawMonthBox(Context context, Canvas canvas) {
        // for now, hard-coded to the 9-oclock position
        String m = localMonthDay();
        String d = localDayOfWeek();
        float x1, y1;
        x1 = clockX(45, .85f);
        y1 = clockY(45, .85f);

        Paint.FontMetrics metrics = smWhite.getFontMetrics();
        float dybottom = -metrics.ascent-metrics.leading; // smidge it up a bunch
        float dytop = -metrics.descent; // smidge it down a little

        drawShadowText(canvas, d, x1, y1+dybottom, smWhite, smBlack);
        drawShadowText(canvas, m, x1, y1+dytop, smWhite, smBlack);

    }

    private void drawShadowText(Canvas canvas, String text, float x, float y, Paint paint, Paint shadowPaint) {
        // TODO: sort out how to render the text as an outline and thus shrink this from 26 drawText calls to two of them
        for(int sx=-2; sx<=2; sx++)
            for(int sy=-2; sy<=2; sy++)
                canvas.drawText(text, x - sx*shadow, y - sy*shadow, shadowPaint);

        canvas.drawText(text, x, y, paint);
    }

    static private boolean debugMetricsPrinted = false;
    private volatile Path facePathCache = null;
    private volatile int facePathCacheMode = -1;

    public void drawFace(Context context, Canvas canvas) {
        Path p = facePathCache; // make a local copy, avoid concurrency crap
        // draw thin lines (indices)

        // check if we've already rendered the face
        if(faceMode != facePathCacheMode || p == null) {
            p = new Path();
            Log.v("ClockFace", "rendering new face, faceMode(" + faceMode + ")");

            if (faceMode == FACE_TOOL)
                for (int i = 1; i < 60; i++)
                    if(i%5 != 0)
                        drawRadialLine(p, smWhite.getStrokeWidth(), i, .9f, 1.0f, false);

            float strokeWidth;

            if (faceMode == FACE_LITE || faceMode == FACE_NUMBERS)
                strokeWidth = smWhite.getStrokeWidth();
            else
                strokeWidth = white.getStrokeWidth();


            for (int i = 0; i < 60; i += 5) {
                if (i == 0) { // top of watch: special
                    if (faceMode != FACE_NUMBERS) {
                        drawRadialLine(p, strokeWidth, -0.4f, .8f, 1.0f, true);
                        drawRadialLine(p, strokeWidth, 0.4f, .8f, 1.0f, true);
                    }
                } else if (i == 45) { // 9 o'clock, don't extend into the inside
                    drawRadialLine(p, strokeWidth, i, 0.9f, 1.0f, false);
                } else {
                    // we want lines for 1, 2, 4, 5, 7, 8, 10, and 11 no matter what
                    if (faceMode != FACE_NUMBERS || !(i == 15 || i == 30))
                        drawRadialLine(p, strokeWidth, i, .8f, 1.0f, false);
                }
            }

            facePathCache = p;
            facePathCacheMode = faceMode;
        }

        // concurrency note: even if some other thread sets the facePathCache to null,
        // the local copy we allocated here will survive for the drawPath we're about to do
        canvas.drawPath(p, smWhite);
        canvas.drawPath(p, superThinBlack);

        if(faceMode == FACE_NUMBERS) {
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
            y = clockY(0, r) - metrics.ascent / 2f;

            white.setTextAlign(Paint.Align.CENTER);
            black.setTextAlign(Paint.Align.CENTER);
            drawShadowText(canvas, "12", x, y, white, black);

            if(!debugMetricsPrinted) {
                debugMetricsPrinted = true;
                Log.v("ClockFace", "x(" + Float.toString(x) + "), y(" + Float.toString(y) + "), metrics.descent(" + Float.toString(metrics.descent) + "), metrics.ascent(" + Float.toString(metrics.ascent) + ")");
            }

            //
            // 3 o'clock
            //

            r = 0.9f;
            float threeWidth = white.measureText("3");

            x = clockX(15, r) - threeWidth / 2f;
            y = clockY(15, r) - metrics.ascent / 2f - metrics.descent / 2f; // empirically gets the middle of the "3" -- actually a smidge off with Roboto but close enough for now and totally font-dependent with no help from metrics

            white.setTextAlign(Paint.Align.CENTER);
            black.setTextAlign(Paint.Align.CENTER);
            drawShadowText(canvas, "3", x, y, white, black);

            //
            // 6 o'clock
            //

            r = 0.9f;

            x = clockX(30, r);
            y = clockY(30, r) + metrics.descent;

            white.setTextAlign(Paint.Align.CENTER);
            black.setTextAlign(Paint.Align.CENTER);
            drawShadowText(canvas, "6", x, y, white, black);
        }
    }

    public void drawHands(Context context, Canvas canvas) {
        TimeZone tz = TimeZone.getDefault();
        int gmtOffset = tz.getRawOffset() + tz.getDSTSavings();
        long time = System.currentTimeMillis() + gmtOffset;

        double seconds = time / 1000.0;
        double minutes = seconds / 60.0;
        double hours = minutes / 12.0;  // because drawRadialLine is scaled to a 60-unit circle

        drawRadialLine(canvas, minutes, 0.1f, 0.9f, white, superThinBlack);
        drawRadialLine(canvas, hours, 0.1f, 0.6f, white, superThinBlack);

        if(showSeconds) {
            // ugly details: we might run 10% or more away from our targets at 4Hz, making the second
            // hand miss the indices. Ugly. Thus, some hackery.
            if(clipSeconds) seconds = Math.floor(seconds * freqUpdate) / freqUpdate;
            drawRadialLine(canvas, seconds, 0.1f, 0.95f, smRed, superThinBlack);
        }
    }

    // private static int calendarTicker = 0;
    private long stippleTimeCache = -1;
    private Path stipplePathCache = null;

    private int maxLevel;
    private List<EventWrapper> eventList;

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    public void setEventList(List<EventWrapper> eventList) {
        this.eventList = eventList;
    }

    public void drawCalendar(Context context, Canvas canvas) {
        if(eventList == null) return; // again, must not be ready yet

        TimeZone tz = TimeZone.getDefault();
        int gmtOffset = tz.getRawOffset() + tz.getDSTSavings();

        for(EventWrapper eventWrapper: eventList) {
            double arcStart, arcEnd;
            WireEvent e = eventWrapper.getWireEvent();
            // this turns out to have a bug if the start/end straddle midnight; this whole
            // business was to deal with float having inadequate resolution to deal with
            // milliseconds since the epoch. For now, we're leaving this out.

            // arcStart = (secondsSinceMidnight(e.startTime)) / 720000.0;
            // arcEnd = (secondsSinceMidnight(e.endTime)) / 720000.0;
            arcStart = (e.startTime + gmtOffset) / 720000.0;
            arcEnd = (e.endTime + gmtOffset) / 720000.0;

            // path caching happens inside drawRadialArc
            drawRadialArc(canvas, eventWrapper.getPathCache(), arcStart, arcEnd,
                    calendarRingMaxRadius - e.minLevel * calendarRingWidth / (maxLevel+1),
                    calendarRingMaxRadius - (e.maxLevel+1) * calendarRingWidth / (maxLevel+1),
                    eventWrapper.getPaint(), outlineBlack);
        }

        // Lastly, draw a stippled pattern at the current hour mark to delineate where the
        // twelve-hour calendar rendering zone starts and ends.

        // integer division gets us the exact hour, then multiply by 5 to scale to our
        // 60-second circle
        long stippleTime = (System.currentTimeMillis() + gmtOffset) / (1000 * 60 * 60);
        stippleTime *= 5;

        // we might want to rejigger this to be paranoid about concurrency smashing stipplePathCache,
        // but it's less of a problem here than with the watchFace, because the external UI isn't
        // inducing the state here to change
        if(stippleTime != stippleTimeCache || stipplePathCache == null) {
            stipplePathCache = new Path();
            stippleTimeCache = stippleTime;

            /*
            if(calendarTicker % 1000 == 0)
                Log.v("ClockFace", "StippleTime(" + stippleTime +
                        "),  currentTime(" + Float.toString((System.currentTimeMillis() + gmtOffset) / 720000f) + ")");
            */

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

                /*
                if(calendarTicker % 1000 == 0)
                    Log.v("ClockFace", "x1(" + Float.toString(x1) + "), y1(" + Float.toString(y1) +
                            "), x2(" + Float.toString(x1) + "), y2(" + Float.toString(y2) +
                            "), xlow(" + Float.toString(xlow) + "), ylow(" + Float.toString(ylow) +
                            "), xhigh(" + Float.toString(xhigh) + "), yhigh(" + Float.toString(yhigh) +
                            ")");

                calendarTicker++;
                */
            }
        }
        canvas.drawPath(stipplePathCache, black);
    }

    /**
     * Given an absolute time (i.e., msec since the epoch), adjust for time zone and DST,
     * and return msec since midnight -- a much smaller number suitable for representation without
     * a full 64 bits
     */
    public long secondsSinceMidnight(long time) {
        TimeZone tz = TimeZone.getDefault();
        int gmtOffset = tz.getRawOffset() + tz.getDSTSavings();

        return (time + gmtOffset) % (24*60*60*1000);
    }

    public void setSize(int width, int height) {
        cx = width / 2;
        cy = height / 2;
        radius = (cx > cy) ? cy : cx; // minimum of the two
        float textSize = radius / 3f;
        float smTextSize = radius / 8f;
        float lineWidth = radius / 20f;

        shadow = lineWidth / 20f;  // for drop shadows

        white.setTextSize(textSize);
        yellow.setTextSize(textSize);
        gray.setTextSize(textSize);
        black.setTextSize(textSize);

        white.setStrokeWidth(lineWidth);
        yellow.setStrokeWidth(lineWidth);
        gray.setStrokeWidth(lineWidth);
        black.setStrokeWidth(lineWidth);

        smWhite.setTextSize(smTextSize);
        smYellow.setTextSize(smTextSize);
        smBlack.setTextSize(smTextSize);
        smWhite.setStrokeWidth(lineWidth /3);
        smYellow.setStrokeWidth(lineWidth /3);
        smBlack.setStrokeWidth(lineWidth /4);
        smRed.setStrokeWidth(lineWidth /3);

        outlineBlack.setStrokeWidth(lineWidth /3);
        superThinBlack.setStrokeWidth(lineWidth / 8);
    }

    // clock math
    public float clockX(double seconds, float fractionFromCenter) {
        double angleRadians = ((seconds - 15) * Math.PI * 2f) / 60.0;
        return (float)(cx + radius * fractionFromCenter * Math.cos(angleRadians));
    }

    public float clockY(double seconds, float fractionFromCenter) {
        double angleRadians = ((seconds - 15) * Math.PI * 2f) / 60.0;
        return (float)(cy + radius * fractionFromCenter * Math.sin(angleRadians));
    }

    public static String localTimeString() {
        String format = "HH:mm:ss.SSS";
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(new Date());
    }

    public static String localMonthDay() {
        return DateUtils.formatDateTime(null, System.currentTimeMillis(), DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_DATE);
    }

    public static String localDayOfWeek() {
        String format = "cccc";
        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
        return sdf.format(new Date());
    }
}
