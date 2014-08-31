package org.dwallach.calwatch;

import java.util.List;
import java.util.Observable;

/**
 * Created by dwallach on 8/25/14.
 */
public class ClockFaceStub extends Observable {
    protected volatile int faceMode = Constants.DefaultWatchFace;
    protected volatile boolean showSeconds = Constants.DefaultShowSeconds;
    protected volatile List<EventWrapper> eventList = null;

    public void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;

        setChanged();
        notifyObservers();
    }

    public int getFaceMode() {
        return faceMode;
    }


    public void setShowSeconds(boolean b) {
        showSeconds = b;

        setChanged();
        notifyObservers();
    }

    public boolean getShowSeconds() {
        return showSeconds;
    }

    public void setEventList(List<EventWrapper> eventList) {
        this.eventList = eventList;

        setChanged();
        notifyObservers();
    }

    public List<EventWrapper> getEventList() {
        return eventList;
    }
}
