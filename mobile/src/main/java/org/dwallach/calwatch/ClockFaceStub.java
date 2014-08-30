package org.dwallach.calwatch;

/**
 * Created by dwallach on 8/25/14.
 */
public class ClockFaceStub {
    private volatile int faceMode = Constants.DefaultWatchFace;

    public void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;
    }

    public int getFaceMode() {
        return faceMode;
    }

    private volatile boolean showSeconds = Constants.DefaultShowSeconds;

    public void setShowSeconds(boolean b) {
        showSeconds = b;
    }

    public boolean getShowSeconds() {
        return showSeconds;
    }
}
