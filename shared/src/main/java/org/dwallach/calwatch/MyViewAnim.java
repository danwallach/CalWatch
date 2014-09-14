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

public class MyViewAnim extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "MyViewAnim";

    private static final boolean debugTracingDesired = false;

    private PanelThread drawThread;
    private volatile ClockFace clockFace;
    private volatile TimeAnimator animator;
    private Context context;
    private volatile boolean activeDrawing = false;

    private static long debugStopTime;
    private static boolean debugTracingOn = false;

    public ClockFace getClockFace() { return clockFace; }

    public MyViewAnim(Context context) {
        super(context);
        setup(context);
    }

    public MyViewAnim(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup(context);
    }

    private void setup(Context ctx) {
        Log.v(TAG, "setup!");
        this.context = ctx;
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

        clockFace = new ClockFace();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // clockFace.drawEverything(canvas);
        // For now, we're doing *nothing* here. Instead, all the drawing is going
        // to happen on the PanelThread or Animator (whichever we're doing in the end)
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "Drawing surface changed!");
        resume();
        clockFace.setSize(width, height);

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "Drawing surface created!");
        resume();
    }


    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "Drawing surface destroyed!");
        stop();
    }

    public void pause() {
        Log.v(TAG, "pausing animation");
        animator.pause();
//        clockFace.setAmbientMode(true);
    }

    public void resume() {
        if(clockFace == null)
            clockFace = new ClockFace();

//        clockFace.setAmbientMode(false);
        clockFace.wipeCaches();
        activeDrawing = true;

        if(animator != null) {
            Log.v(TAG, "resuming old animator!");
            animator.resume();
        } else {
            Log.v(TAG, "new animator starting");
            animator = new TimeAnimator();
            animator.setTimeListener(new MyTimeListener(getHolder(), context));
            // animator.setFrameDelay(1000);  // doesn't actually work?

            if(drawThread == null) {
                Log.v(TAG, "starting draw thread from scratch");
                drawThread = new PanelThread(animator); // will start the animator
                drawThread.start();
            } else {
                Log.v(TAG, "asking previous draw thread to start a new animator");
                // animator.start() needs to happen on the PanelThread, not this one
                Handler handler = drawThread.getHandler();
                final Animator fa = animator;

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        fa.start();
                    }
                });
            }
        }
    }

    public void stop() {
        Log.v(TAG, "stopping animation!");

        activeDrawing = false;
        if(animator != null) {
            // new experimental ways to maybe quit things
            if(drawThread == null) {
                Log.v(TAG, "no draw thread around to kill ?!");
            } else {
                // animator.start() needs to happen on the PanelThread, not this one
                Handler handler = drawThread.getHandler();

                // it's weird, but this happens some times
                if(handler != null) {
                    Looper looper = handler.getLooper();
                    if (looper != null)
                        looper.quitSafely();
                }

                // maybe these will do something; it's unclear
                animator.end();
                animator.cancel();

                animator = null;
                drawThread = null;
                clockFace.destroy();
                clockFace = null;
            }
        }
    }

    class MyTimeListener implements TimeAnimator.TimeListener {
        private SurfaceHolder surfaceHolder;
        private Context context;
        // private double fps = 0.0;

        public MyTimeListener(SurfaceHolder surfaceHolder, Context context) {
            this.surfaceHolder = surfaceHolder;
            this.context = context;

            Log.v(TAG, "Time listener is up!");
        }

        private int ticks = 0;

        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            ticks++;

            // we have a weird race condition when the screen turns off and blanks out
            if(!activeDrawing) {
                if(ticks % 1000 == 0)
                    Log.v(TAG, "onTimeUpdate being called while we're not needing it");
                return;
            }

            ClockFace localClockFace = clockFace; // local cached copy, to deal with concurrency issues
            TimeWrapper.update();
            Canvas c = null;

            try {
                try {
                    c = surfaceHolder.lockCanvas(null);
                } catch (java.lang.IllegalStateException e) {
                    if(ticks % 1000 == 0) Log.w(TAG, "Failed to get a canvas for drawing!");
                    c = null;
                }

                if(c == null) {
                    Log.w(TAG, "no canvas yet?");
                    return;
                }

                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                localClockFace.drawEverything(c);

                if(debugTracingDesired) {
                    TimeWrapper.update();
                    long currentTime = TimeWrapper.getGMTTime();
                    if (currentTime > debugStopTime && debugTracingOn) {
                        debugTracingOn = false;
                        Debug.stopMethodTracing();
                    }
                }

                ClockState clockState = ClockState.getSingleton();

                // TODO add support for ambient mode, do the same thing
                if(clockState == null) {
                    Log.e(TAG, "whoa, no clock state?!");
                    return;
                }
                if(!clockState.getShowSeconds() || localClockFace.getAmbientMode()) {
                    // if we're not showing the second hand, then we don't need to refresh at 50+Hz
                    // so instead we sleep one second; regardless, the minute hand will move
                    // very, very smoothly.
                    SystemClock.sleep(1000);

                }
            } finally {
                if (c != null) {
                    surfaceHolder.unlockCanvasAndPost(c);
                }
            }
        }
    }
    /**
     * Understanding a Looper: http://stackoverflow.com/questions/7597742/what-is-the-purpose-of-looper-and-how-to-use-it
     */
    class PanelThread extends Thread {
        Handler handler = null;
        Animator animator;

        public PanelThread(Animator animator) {
            this.animator = animator;
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
                animator.start();

                // now, the handler will automatically bind to the
                // Looper that is attached to the current thread
                // You don't need to specify the Looper explicitly
                handler = new Handler();

                // After the following line the thread will start
                // running the message loop and will not normally
                // exit the loop unless a problem happens or you
                // quit() the looper (see below)
                Looper.loop();

                Log.v(TAG, "looper finished!");
            } catch (Throwable t) {
                Log.e(TAG, "looper halted due to an error", t);
            }
        }
    }
}

