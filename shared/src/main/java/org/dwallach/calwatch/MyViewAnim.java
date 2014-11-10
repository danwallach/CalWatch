/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.animation.Animator;
import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;

public class MyViewAnim extends SurfaceView implements SurfaceHolder.Callback, Observer {

    class ZombieException extends RuntimeException {
        public ZombieException(String s) {
            super(s);
        }
    }

    private static final String TAG = "MyViewAnim";

    private static final boolean debugTracingDesired = false;

    private static volatile int numViewAnims = 0;

    private volatile PanelThread drawThread;
    private volatile ClockFace clockFace;
    private volatile TimeAnimator animator;
    private volatile boolean drawingMaxHertz = false;
    private volatile boolean drawingAmbientMode = false;
    private boolean sleepInEventLoop = false;

    private boolean drawThreadDesired = true;

    private static long debugStopTime;
    private static boolean debugTracingOn = false;

    public ClockFace getClockFace() { return clockFace; }

    public void setAmbientMode(boolean ambientMode) {
        drawingAmbientMode = ambientMode;
    }

    public MyViewAnim(Context context) {
        super(context);
        setup(context);
    }

    public MyViewAnim(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }

    public boolean getDrawThreadDesired() {
        return drawThreadDesired;
    }

    private void setDrawThreadDesired(boolean drawThreadDesired) {
        Log.v(TAG, "draw thread desired: " + drawThreadDesired);
        if(this.drawThreadDesired == drawThreadDesired) return;

        this.drawThreadDesired = drawThreadDesired;

        // okay, if we get here, the desired state has changed, so we need to
        // either kill the running thread, or start it back up again
        if(drawThreadDesired)
            startDrawThread();
        else
            killDrawThread();
    }

    private void setup(Context ctx) {
        numViewAnims++;
        Log.v(TAG, "setup! numViewAnims: " + numViewAnims);
        getHolder().addCallback(this);

        /*
         * Set up debug performance traces
         */
        if(debugTracingDesired) {
            Log.v(TAG, "setting up debug tracing");
            TimeWrapper.update();
            debugStopTime = TimeWrapper.getGMTTime() + 60 * 1000;  // 60 seconds
            Debug.startMethodTracing("calwatch");
            debugTracingOn = true;
            Log.v(TAG, "done!");
        }

        if(clockFace == null) {
            Log.v(TAG, "initializing ClockFace");
            clockFace = new ClockFace();
        }

        ClockState clockState = ClockState.getSingleton();
        if(clockState == null) {
            Log.e(TAG, "super bad null clockState!!???!");
        } else {
            clockState.addObserver(this);
            boolean showSeconds = clockState.getShowSeconds();
            setDrawThreadDesired(showSeconds);
        }
    }

    /**
     * Set this to true if you want the event loop, maintained by the MyViewAnim, to internally
     * have a 1-sec sleep that it does when the seconds-hand isn't visible. This defaults to
     * false, under the assumption that you've got some external process (alarms/intents/whatever)
     * that drives the refresh rates under such low-frequency circumstances.
     * @param sleepInEventLoop
     */
    public void setSleepInEventLoop(boolean sleepInEventLoop) {
        this.sleepInEventLoop = sleepInEventLoop;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        redrawClockSlow("view:onDraw");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "Drawing surface changed!");
        clockFace.setSize(width, height);
        redrawClockSlow("view:surfaceChanged");
//        resumeMaxHertz();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "Drawing surface created!");
        redrawClockSlow("view:surfaceCreated");
//        resumeMaxHertz();
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "Drawing surface destroyed!");
//        stop();
    }

    public void pause() {
        Log.v(TAG, "pause requested, stopping");
        stop();
//        clockFace.setAmbientMode(true);
    }

    public void resumeMaxHertz() {
        Log.v(TAG, "resumeMaxHertz");
        if (clockFace == null)
            clockFace = new ClockFace();

//        clockFace.setAmbientMode(false);
        clockFace.wipeCaches();
        drawingMaxHertz = true;

        if (surfaceHolder == null)
            surfaceHolder = getHolder();

        // we'll only start the drawThread if necessary, which is to say, if we're asked for the seconds hand
        if(drawThreadDesired)
            startDrawThread();
    }

    private void startDrawThread() {
        try {
            LockWrapper.lock(); // possible weird cases of concurrency here; we want to ensure that there is never more than one drawThread

            if (!drawingMaxHertz) return;


            if (drawThread != null) {
                Log.v(TAG, "draw thread already exists, doing nothing");
                return;
            }


            try {
                Log.v(TAG, "starting new animator");
                animator = new TimeAnimator();
                animator.setTimeListener(new MyTimeListener());

                Log.v(TAG, "starting new draw thread");
                drawThread = new PanelThread(animator); // will start the animator
                drawThread.start();
            } catch (Throwable t) {
                Log.e(TAG, "unexpected failure in resumeMaxHertz()", t);
            }
        } finally {
            LockWrapper.unlock();
        }
    }

    public void stop() {
        Log.v(TAG, "stopping animation!");
        drawingMaxHertz = false;
        killDrawThread();
        surfaceHolder = null;
    }

    private void killDrawThread() {
        Log.v(TAG, "killDrawThread");
        if (drawThread == null) {
            Log.v(TAG, "no draw thread around to kill ?!");
        } else {
            try {
                // animator.start() needs to happen on the PanelThread, not this one
                Handler handler = drawThread.getHandler();

                // it's weird, but this happens some times
                if (handler != null) {
                    Looper looper = handler.getLooper();
                    if (looper != null)
                        looper.quitSafely();
                }
            } catch (Throwable t) {
                Log.e(TAG, "unexpected failure in stop()", t);
            }

            // these seem to be required to happen on the same thread as where the drawing is happening, so does nothing here
//                animator.end();
//                animator.cancel();

            animator = null;
            drawThread = null;
//                clockFace.destroy();
//                clockFace = null;
        }
    }

    private int ticks = 0;

    // redrawClock and redrawClockSlow are called by everybody on the outside. The slow one is meant
    // to be called occasionally while the other is meant to be called at 60Hz. The string helps us
    // keep track of all the many, many different ways that this seems to be called.

    private HashMap<String, Integer> redrawSlowSources = null;

    /**
     * dump all the sources collected from redrawClockSlow into the logcat, then clear it out
     */
    public void reportSlowSources() {
        for(String key : redrawSlowSources.keySet())
            Log.v(TAG, "redrawSlow: " + key + " -> " + redrawSlowSources.get(key));
        redrawSlowSources = null;
    }

    public void redrawClockSlow(String source) {
        if (redrawSlowSources == null)
            redrawSlowSources = new HashMap<String, Integer>();

        if(redrawSlowSources.containsKey(source))
            redrawSlowSources.put(source, redrawSlowSources.get(source) + 1);
        else
            redrawSlowSources.put(source, 1);

        // In the seemingly common case where a timer goes off while we've got the draw thread up
        // and running, so a redraw is coming in from that timer, we want to do nothing and to fail
        // fast. No locking to avoid stuttering in the redraw process.
        if(drawThread != null) {
            ticks++;
            TimeWrapper.frameSkip();
            return;
        }

        redrawClock(source);
    }

    public void redrawClock() {
        redrawClock(null);
    }

    public void redrawClock(String source) {
        LockWrapper.lock();

        try {
            ticks++;

            if (clockFace == null) {
                Log.e(TAG, "tick without a clock!");
                return;
            }

            if(drawThread != null && Thread.currentThread() != drawThread) {
                Log.e(TAG, "Whoa, drawing from multiple draw threads going on! Not good. (Source: " + source + ")");
                throw new ZombieException("zombie draw thread detected");
            } else if (drawingMaxHertz || drawingAmbientMode) {
                redrawInternal();
            } else {
                TimeWrapper.frameSkip();
                if (ticks % 1000 == 0)
                    Log.v(TAG, "redraw called while !drawingMaxHertz; ignoring");
            }
        } finally {
            LockWrapper.unlock();
        }
    }

    private volatile SurfaceHolder surfaceHolder = null;

    // Currently only called from redrawClock(), above; Less error checking here.
    private void redrawInternal() {
        ClockFace localClockFace = clockFace; // local cached copy, to deal with concurrency issues
        SurfaceHolder localSurfaceHolder = surfaceHolder; // local cached copy, to deal with concurrency issues
        TimeWrapper.update();
        Canvas c = null;

        if (localSurfaceHolder == null) {
            localSurfaceHolder = surfaceHolder = getHolder();
            if (localSurfaceHolder == null) {
                Log.e(TAG, "still no surface holder, giving up");
                    return;
            } else {
                    Log.v(TAG, "success getting new surface holder");
            }
        }

        // Digression on surface holders: these seem to be necessary in order to draw anything. The problem
        // seems to occur when the watch is busy putting itself to sleep while simultaneously we're trying
        // to draw to the screen on this separate thread. There seem to be one or more redraws that will
        // be attempted after the canvas and such is long gone. This is a source of weird exceptions and
        // general badness.

        // Original solution: when the stop() method is called (on the UI thread), it needs to stop the Looper
        // which is doing all of the redrawing. Thus, the whole quitSafely() business and killing the animator.
        // Yet still, there would sometimes be another round-trip through the redrawing.

        // Kludgy solution: stop() now also nulls out the surface holder (a volatile private member variable,
        // so we'll notice this very quickly on the drawing thread). Because this could happen at any time,
        // we sample the contents of the variable once at the top of this function. That is, once we're committed
        // to rendering, we're going to see it through, but subsequent trips through here will do nothing.

        // The *real* solution, hopefully, is the proper use of the drawingMaxHertz flag, also a volatile. Everything
        // that might be trying to say "whoa, time to be done with drawing" should call stop() on this view, which
        // will set that flag to false, which will then, in turn, ensure that subsequent redraw calls never even
        // get into redrawInternal.

        try {
            try {
                c = localSurfaceHolder.lockCanvas(null);
            } catch (java.lang.IllegalStateException e) {
                if (ticks % 1000 == 0) Log.e(TAG, "Failed to get a canvas for drawing!");
                c = null;
            } catch (Throwable throwable) {
                // this should never happen
                Log.e(TAG, "unknown exception when trying to lock canvas", throwable);
                c = null;
            }

            if (c == null) {
                if (ticks % 1000 == 0) Log.e(TAG, "null canvas, can't draw anything");
                return;
            }

            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            localClockFace.drawEverything(c);

            if (debugTracingDesired) {
                TimeWrapper.update();
                long currentTime = TimeWrapper.getGMTTime();
                if (currentTime > debugStopTime && debugTracingOn) {
                    debugTracingOn = false;
                    Debug.stopMethodTracing();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "something blew up while redrawing", t);
        } finally {
            if (c != null) {
                try {
                    localSurfaceHolder.unlockCanvasAndPost(c);
                } catch (Throwable t) {
                    Log.e(TAG, "something blew up while unlocking the canvas", t);
                }
            }

            // Warning Will Robinson! Danger! With the new LockWrapper structure, turning
            // on this flag is likely to set up all kinds of deadlock action. We should really
            // just delete sleepInEventLoop altogether since we now get this effect through
            // the seconds-scale alarm.
            if (sleepInEventLoop)
                SystemClock.sleep(1000);

        }
    }

    /**
     * This is called when the ClockState updates itself; we need this to detect and
     * deal with the case when the user changes whether or not they want a second-hand
     * @param observable
     * @param o
     */
    @Override
    public void update(Observable observable, Object o) {
        Log.v(TAG, "update from ClockState");
        ClockState clockState = ClockState.getSingleton();
        if(clockState == null)
            Log.e(TAG, "null ClockState?!!");
        else {
            boolean showSeconds = clockState.getShowSeconds();
            setDrawThreadDesired(showSeconds);
            redrawClockSlow("view:update"); // do this immediately or it will take a while for the alarm to catch up
        }
    }

    class MyTimeListener implements TimeAnimator.TimeListener {
        public MyTimeListener() {
            Log.v(TAG, "Time listener is up!");
        }

        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            // this is the one place where we call redrawClock rather than redrawClockSlow,
            // since this callback comes to us from the animator, from the looper, from the drawThread
            try {
                redrawClock();
            } catch (ZombieException e) {
                // This generally signals that bad things are afoot and that we, this thread,
                // aren't meant to exist and must really and truly die. That, in turns, suggests
                // we've got multiple loopers going on, and life is just all around bad times.
                // This sounds insane, and nobody even believes in zombies, but then some of the
                // behavior I've seen suggests that it *might* be happening. Thus zombie paranoia.

                try {
                    animation.cancel();
                    PanelThread zombieThread = (PanelThread) Thread.currentThread();
                    Handler handler = zombieThread.getHandler();

                    if (handler != null) {
                        Looper looper = handler.getLooper();
                        if (looper != null)
                            looper.quitSafely();
                    }
                    Log.i(TAG, "zombie apocalypse successfully fended off. For now.");
                } catch (Throwable t) {
                    Log.e(TAG, "failed to stop the zombie apocalypse", t);
                }
            }
        }
    }
    /**
     * Understanding a Looper: http://stackoverflow.com/questions/7597742/what-is-the-purpose-of-looper-and-how-to-use-it
     */
    class PanelThread extends Thread {
        Handler handler = null;
        Animator localAnimator;

        public PanelThread(Animator animator) {
            this.localAnimator = animator;
        }

        public Handler getHandler() {
            return handler;
        }

        @Override
        public void run() {
            try {
                // preparing a looper on current thread
                // the current thread is being detected implicitly
                Looper.prepare();

                // this needs to happen on the same thread
                localAnimator.start();

                // now, the handler will automatically bind to the
                // Looper that is attached to the current thread
                // You don't need to specify the Looper explicitly
                handler = new Handler();

                // After the following line the thread will start
                // running the message loop and will not normally
                // exit the loop unless a problem happens or you
                // quit() the looper (see below)
                Looper.loop();

                // We'll only get here if somebody's trying to tear down
                // the Looper.

                Log.v(TAG, "looper finished!");
            } catch (Throwable t) {
                Log.e(TAG, "looper halted due to an error", t);
            } finally {
                try {
                    localAnimator.cancel();
                } catch (Throwable t2) {
                    Log.e(TAG, "can't clean up animator, ignoring", t2);
                }
                animator = null;
                drawThread = null;
            }
        }
    }
}

