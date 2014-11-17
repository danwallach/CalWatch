/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
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
import java.util.concurrent.locks.Lock;

public class ClockState extends Observable {
    private final static String TAG = "ClockState";

    public final static int FACE_TOOL = 0;
    public final static int FACE_NUMBERS = 1;
    public final static int FACE_LITE = 2;

    private int faceMode = Constants.DefaultWatchFace;
    private boolean showSeconds = Constants.DefaultShowSeconds;
    private boolean showDayDate = Constants.DefaultShowDayDate;
    private List<EventWrapper> eventList = null;
    private List<EventWrapper> visibleEventList = null;
    private int maxLevel = 0;

    private static ClockState singleton = null;

    private boolean wireInitialized = false;

    /**
     * Query whether or not a wire update has arrived yet. If the result is false,
     * *and* we're running on the watch, then we've only got default values. If we're
     * running on the phone, this data structure might be otherwise initialized, so
     * this getter might return false even when the data structure is loaded with events.
     * @return if a wire message has successfully initialized the clock state
     */
    public boolean getWireInitialized() {
        return wireInitialized;
    }


    // we don't want others constructing this
    private ClockState() { }

    // instead, they'll go through this
    public static ClockState getSingleton() {
        if(singleton == null)
            singleton = new ClockState();

        return singleton;
    }

    public void setFaceMode(int faceMode) {
        // warning: this might come in from another thread!
        this.faceMode = faceMode;

//        pingObservers();
    }

    public int getFaceMode() {
        return faceMode;
    }

    public void setShowSeconds(boolean showSeconds) {
        this.showSeconds = showSeconds;

//        pingObservers();
    }

    public boolean getShowSeconds() {
        return showSeconds;
    }

    public void setShowDayDate(boolean showDayDate) {
        this.showDayDate = showDayDate;

//        pingObservers();
    }

    public boolean getShowDayDate() {
        return showDayDate;
    }


    /**
     * Load the eventlist. This is meant to consume the output of the calendarFetcher,
     * which is in GMT time, *not* local time.
     * @param eventList list of events (GMT time)
     */
    public void setEventList(List<EventWrapper> eventList) {
        LockWrapper.lock();
        try {
            this.eventList = eventList;
            this.visibleEventList = null;
            pingObservers();
        } finally {
            LockWrapper.unlock();
        }
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
     * and does it *without* clipping to the visible watchface. These will be in GMT time.
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
//        if(eventList == null) {
//            Log.v(TAG, "no events to compute visibility over, going with empty list for now");
//        }

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

//        Log.v(TAG, "clipStart: " + TimeWrapper.formatGMTTime(clipStartMillis) + " (" + clipStartMillis +
//                "), clipEnd: " + TimeWrapper.formatGMTTime(clipEndMillis) + " (" + clipEndMillis + ")");

        lastClipStartTime = clipStartMillis;
        visibleEventList = new ArrayList<EventWrapper>();

        if(eventList != null) {
            Log.v(TAG, "clipping " + eventList.size() + " raw events to fit the screen");
            for (EventWrapper eventWrapper : eventList) {
                WireEvent e = eventWrapper.getWireEvent();

                long startTime = e.startTime;
                long endTime = e.endTime;

//                Log.v(TAG, "New event: startTime: " + TimeWrapper.formatGMTTime(startTime) +
//                        ", endTime: " + TimeWrapper.formatGMTTime(endTime));

                // clip the event to the screen
                if (startTime < clipStartMillis) startTime = clipStartMillis;
                if (endTime > clipEndMillis) endTime = clipEndMillis;


//                Log.v(TAG, "-- Clipped: startTime: " + TimeWrapper.formatGMTTime(startTime) +
//                        ", endTime: " + TimeWrapper.formatGMTTime(endTime));


                if (endTime < clipStartMillis || startTime > clipEndMillis)
                    continue; // this one is off-screen

                visibleEventList.add((new EventWrapper(new WireEvent(startTime + gmtOffset, endTime + gmtOffset, e.displayColor))));
            }
        }

        // now, we run off and do screen layout
        if(eventList != null) {
            // first, try the fancy constraint solver
            if(EventLayoutUniform.go(visibleEventList)) {
                // yeah, we succeeded
                this.maxLevel = EventLayoutUniform.MAXLEVEL;
            } else {
                // something blew up with the Simplex solver, fall back to the cheesy, greedy algorithm
                Log.v(TAG, "falling back to older greedy method");
                this.maxLevel = EventLayout.go(visibleEventList);
            }
        } else
            this.maxLevel = 0;

        EventLayout.sanityTest(visibleEventList, this.maxLevel, "After new event layout");
        Log.v(TAG, "maxLevel for new events: " + this.maxLevel);
        Log.v(TAG, "number of new events: " + visibleEventList.size());

//        debugDump();
    }

    /**
     * This returns a list of *visible* events on the watchface, cropped to size, and adjusted to
     * the *local* timezone. If you want GMT events, which will not have been clipped, then use
     * getWireEventList().
     */
    public List<EventWrapper> getVisibleLocalEventList() {
        computeVisibleEvents(); // should be fast, since mostly it will detect that nothing has changed
        return visibleEventList;
    }

    /**
     * Load the ClockState with a protobuf containing a complete update
     * @param eventBytes a marshalled protobuf of type WireUpdate
     */
    public void setProtobuf(byte[] eventBytes) {
        LockWrapper.lock();

        try {
            Wire wire = new Wire();
            WireUpdate wireUpdate = null;

            try {
                wireUpdate = (WireUpdate) wire.parseFrom(eventBytes, WireUpdate.class);
                wireInitialized = true;
            } catch (IOException ioe) {
                Log.e(TAG, "parse failure on protobuf: nbytes(" + eventBytes.length + "), error(" + ioe.toString() + ")");
                return;
            } catch (Exception e) {
                if (eventBytes.length == 0)
                    Log.e(TAG, "zero-length message received!");
                else
                    Log.e(TAG, "some other weird failure on protobuf: nbytes(" + eventBytes.length + "), error(" + e.toString() + ")");
                return;
            }

            if (wireUpdate.newEvents)
                setWireEventListHelper(wireUpdate.events);

            setFaceMode(wireUpdate.faceMode);
            setShowSeconds(wireUpdate.showSecondHand);
            setShowDayDate(wireUpdate.showDayDate);

            pingObservers();

            Log.v(TAG, "event update complete");
        } finally {
            LockWrapper.unlock();
        }
    }

    /**
     * marshalls into a protobuf for transmission elsewhere
     * @return the protobuf
     */
    public byte[] getProtobuf() {
        WireUpdate wireUpdate = new WireUpdate(getWireEventList(), true, getFaceMode(), getShowSeconds(), getShowDayDate());
        byte[] output = wireUpdate.toByteArray();

        return output;
    }

    public void pingObservers() {
        // this incantation will make observers elsewhere aware that there's new content
        setChanged();
        notifyObservers();
        clearChanged();
    }

    public int getMaxLevel() {
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
