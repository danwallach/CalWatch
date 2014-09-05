package org.dwallach.calwatch;

import android.util.Log;

import com.squareup.wire.Wire;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireUpdate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

public class ClockState extends Observable {
    private final static String TAG = "ClockState";

    public final static int FACE_TOOL = 0;
    public final static int FACE_NUMBERS = 1;
    public final static int FACE_LITE = 2;

    private int faceMode = Constants.DefaultWatchFace;
    private boolean showSeconds = Constants.DefaultShowSeconds;
    private List<EventWrapper> eventList = null;
    private int maxLevel = 0;

    private static ClockState singleton = null;


    // we don't want others constructing this
    private ClockState() { }

    // instead, they'll go through this
    public static ClockState getSingleton() {
        if(singleton == null)
            singleton = new ClockState();

        return singleton;
    }

    // note: all the public methods are synchronized; this deals with the weird case
    // when a big update is coming in and we want to make sure that somebody reading
    // this class gets a consistent view of it
    public synchronized void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;

        pingObservers();
    }

    public synchronized int getFaceMode() {
        return faceMode;
    }


    public synchronized void setShowSeconds(boolean b) {
        showSeconds = b;

        pingObservers();
    }

    public synchronized boolean getShowSeconds() {
        return showSeconds;
    }

    public synchronized void setEventList(List<EventWrapper> eventList) {
        this.eventList = eventList;
        recomputeMaxLevel();
        pingObservers();
    }

    private void setWireEventListHelper(List<WireEvent> wireEventList) {
        List<EventWrapper> results = new ArrayList<EventWrapper>();

        for (WireEvent wireEvent : wireEventList)
            results.add(new EventWrapper(wireEvent));

        setEventList(results);
        recomputeMaxLevel();
        Log.v(TAG, "new calendar event list, " + results.size() + " entries");
    }

    public List<WireEvent> getWireEventList() {
        List<WireEvent> output = new ArrayList<WireEvent>();

        for(EventWrapper event: eventList)
            output.add(event.getWireEvent());

        return output;
    }

    public synchronized void setWireEventList(List<WireEvent> wireEventList) {
        setWireEventListHelper(wireEventList);
        recomputeMaxLevel();
        pingObservers();
    }


    private void recomputeMaxLevel() {
        int maxLevel = 0;
        for(EventWrapper eventWrapper : eventList) {
            int eMaxLevel =  eventWrapper.getWireEvent().maxLevel;
            if(eMaxLevel > maxLevel)
                maxLevel = eMaxLevel;
        }

        this.maxLevel = maxLevel;
        Log.v(TAG, "maxLevel for new events: " + this.maxLevel);
    }

    public synchronized List<EventWrapper> getEventList() {
        return eventList;
    }

    /**
     * Load the ClockState with a protobuf containing a complete update
     * @param eventBytes a marshalled protobuf of type WireUpdate
     */
    public synchronized void setProtobuf(byte[] eventBytes) {
        Wire wire = new Wire();
        WireUpdate wireUpdate = null;

        try {
            wireUpdate = (WireUpdate) wire.parseFrom(eventBytes, WireUpdate.class);
        } catch (IOException ioe) {
            Log.e(TAG, "parse failure on protobuf: nbytes(" + eventBytes.length + "), error(" + ioe.toString() + ")");
            return;
        } catch (Exception e) {
            if(eventBytes.length == 0)
                Log.e(TAG, "zero-length message received!");
            else
                Log.e(TAG, "some other weird failure on protobuf: nbytes(" + eventBytes.length + "), error(" + e.toString() + ")");
            return;
        }

        if (wireUpdate.newEvents)
            setWireEventListHelper(wireUpdate.events);

        setFaceMode(wireUpdate.faceMode);
        setShowSeconds(wireUpdate.showSeconds);

        Log.v(TAG, "event update complete");
    }

    /**
     * marshalls into a protobuf for transmission elsewhere
     * @return the protobuf
     */
    public synchronized byte[] getProtobuf() {
        WireUpdate wireUpdate = new WireUpdate(getWireEventList(), true, getShowSeconds(), getFaceMode());
        byte[] output = wireUpdate.toByteArray();

        return output;
    }

    private void pingObservers() {
        // this incantation will make observers elsewhere aware that there's new content
        setChanged();
        notifyObservers();
        clearChanged();
    }

    public synchronized int getMaxLevel() {
        return maxLevel;
    }
}
