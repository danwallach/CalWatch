package org.dwallach.calwatch;

/**
 * Created by dwallach on 8/25/14.
 */
public class ClockFaceStub {
    public final static int FACE_TOOL = 0;
    public final static int FACE_NUMBERS = 1;
    public final static int FACE_LITE = 2;

    private volatile int faceMode = FACE_TOOL;

    public void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;
    }

    public int getFaceMode() {
        return faceMode;
    }

    private volatile boolean showSeconds = true;

    public void setShowSeconds(boolean b) {
        showSeconds = b;
    }

    public boolean getShowSeconds() {
        return showSeconds;
    }
}
