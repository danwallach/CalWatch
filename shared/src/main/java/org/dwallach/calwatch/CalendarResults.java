/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

import android.graphics.Paint;
import android.graphics.Path;
import android.util.SparseArray;

import org.dwallach.calwatch.proto.WireEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * data structure used for results
 */
public class CalendarResults {
    public static class Instance {
        public long startTime;
        public long endTime;
        public long eventID;
        public int displayColor;
        public boolean allDay;
        public boolean visible;

        public String toString() {
            return "Start(" + startTime +
                    "), End(" + endTime +
                    "), ID(" + eventID +
                    "), displayColor(" + Integer.toHexString(displayColor) +
                    "), allDay(" + allDay +
                    "), visible(" + visible + ")";
        }

        public WireEvent toWireEvent() {
            return new WireEvent(startTime, endTime, displayColor);
        }
    }

    public ArrayList<Instance> instances;

    public CalendarResults() {
        // calendars = new HashMap<Integer, Calendar>();
        instances = new ArrayList<Instance>();
    }

    public List<WireEvent> getWireEvents() {
        List<WireEvent> wireList = new ArrayList<WireEvent>();
        for(Instance instance : instances) {
            wireList.add(instance.toWireEvent());
        }
        return wireList;
    }

    public List<EventWrapper> getWrappedEvents() {
        List<EventWrapper> wireList = new ArrayList<EventWrapper>();
        for(Instance instance : instances) {
            wireList.add(new EventWrapper(instance.toWireEvent()));
        }
        return wireList;
    }
}
