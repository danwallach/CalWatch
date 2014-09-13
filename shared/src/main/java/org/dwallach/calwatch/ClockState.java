package org.dwallach.calwatch;

import android.content.Context;
import android.text.format.DateUtils;
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
    private List<EventWrapper> visibleEventList = null;
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
        this.visibleEventList = null;
        pingObservers();
    }

    private void setWireEventListHelper(List<WireEvent> wireEventList) {
        List<EventWrapper> results = new ArrayList<EventWrapper>();

        for (WireEvent wireEvent : wireEventList)
            results.add(new EventWrapper(wireEvent));

        setEventList(results);
        Log.v(TAG, "new calendar event list, " + results.size() + " entries");
    }

    /**
     * This fetches *every* event present in the ClockState (typically 24 hours worth),
     * and does it *without* clipping to the visible watchface.
     */
    public List<WireEvent> getWireEventList() {
        List<WireEvent> output = new ArrayList<WireEvent>();

        if(eventList == null) return null;

        for(EventWrapper event: eventList)
            output.add(event.getWireEvent());

        return output;
    }

    private long lastClipStartTime = 0;

    private void computeVisibleEvents() {
        if(eventList == null) {
            Log.v(TAG, "no events to compute visibility over, going with empty list for now");
        }

        // This is going to be called on every screen refresh, so it needs to be fast in the common case.
        // The current solution is to measure the time and try to figure out whether we've ticked onto
        // a new hour, which would mean that it's time to redo the visibility calculation. If new events
        // showed up for whatever other reason, then that would have nuked visibleEventList, so we'll
        // recompute that here as well.

//        Log.v(TAG, "starting event pool: " + eventList.size());

        long time = TimeWrapper.getLocalTime();
        int gmtOffset = TimeWrapper.getGmtOffset();

        long clipStartMillis = TimeWrapper.getLocalFloorHour() - gmtOffset; // convert from localtime back to GMT time for looking at events
        long clipEndMillis = clipStartMillis + 43200000; // 12 hours later

        if(visibleEventList != null)
            EventLayout.sanityTest(visibleEventList, this.maxLevel, "Before clipping");

        if(lastClipStartTime == clipStartMillis && visibleEventList != null)
            return; // we've already done it, and we've got a cache of the results

        Log.v(TAG, "clipStart: " + clipStartMillis + " clipEnd: " + clipEndMillis);

        lastClipStartTime = clipStartMillis;
        visibleEventList = new ArrayList<EventWrapper>();

        if(eventList != null)
            for(EventWrapper eventWrapper: eventList) {
                WireEvent e = eventWrapper.getWireEvent();

                long startTime = e.startTime + gmtOffset;
                long endTime = e.endTime + gmtOffset;

                // this clipping is hopefully unnecessary as we've moved it into ClockState
                if (startTime < clipStartMillis) startTime = clipStartMillis;
                if (endTime > clipEndMillis) endTime = clipEndMillis;

                if (endTime < clipStartMillis || startTime > clipEndMillis)
                    continue; // this one is off-screen

                visibleEventList.add((new EventWrapper(new WireEvent(startTime, endTime, e.displayColor))));
            }

        // now, we run off and do our greedy algorithm to fill out the minLevel / maxLevel on each event
        if(eventList != null)
            this.maxLevel = EventLayout.go(visibleEventList);
        else
            this.maxLevel = 0;

        EventLayout.sanityTest(visibleEventList, this.maxLevel, "After clipping");
        Log.v(TAG, "maxLevel for new events: " + this.maxLevel);
        Log.v(TAG, "number of new events: " + visibleEventList.size());

//        debugDump();
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size
     */
    public synchronized List<EventWrapper> getVisibleLocalEventList() {
        computeVisibleEvents(); // should be fast, since mostly it will detect that nothing has changed
        return visibleEventList;
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

    private void debugDump() {
        Log.v(TAG, "All events in the DB:");
        for(EventWrapper e: eventList) {
            Log.v(TAG, "--> displayColor(" + Integer.toHexString(e.getWireEvent().displayColor) + "), minLevel(" + e.getMinLevel() + "), maxLevel(" + e.getMaxLevel() + "), startTime(" + e.getWireEvent().startTime + "), endTime(" + e.getWireEvent().endTime + ")");
        }

        Log.v(TAG, "Visible:");
        for(EventWrapper e: visibleEventList) {
            Log.v(TAG, "--> displayColor(" + Integer.toHexString(e.getWireEvent().displayColor) + "), minLevel(" + e.getMinLevel() + "), maxLevel(" + e.getMaxLevel() + "), startTime(" + e.getWireEvent().startTime + "), endTime(" + e.getWireEvent().endTime + ")");
        }
    }
}
