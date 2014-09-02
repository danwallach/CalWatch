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
    public static class Color {
        public String key;
        public int argb;
        public Paint paint;
    }

    public static class Calendar {
        public int ID;
        public String name;
        public String accountName;
        public String accountType;
        public int calendarColor;
        public String calendarColorKey;
        public boolean visible;
    }

    public static class Event {
        public String title;
        public long startTime;
        public long endTime;
        public boolean allDay;
        public String accountName;
        public String accountType;
        public int calendarID;
        public int eventColor;
        public String eventColorKey;
        public int displayColor;    // boolean, EVENT_COLOR or CALENDAR_COLOR
        public boolean visible;
        public String rDate;
        public String rRule;
        public String exDate;
        public String exRule;
        public String duration;
        public Paint paint;
        public Path path;

        public int minLevel, maxLevel; // filled in by the layout algorithm

         // standard constructor
        public Event() { }

        // copy constructor
        public Event(Event e) {
            this.title = e.title;
            this.startTime = e.startTime;
            this.endTime = e.endTime;
            this.allDay = e.allDay;
            this.accountName = e.accountName;
            this.accountType = e.accountType;
            this.calendarID = e.calendarID;
            this.eventColor = e.eventColor;
            this.eventColorKey = e.eventColorKey;
            this.displayColor = e.displayColor;
            this.visible = e.visible;
            this.rDate = e.rDate;
            this.rRule = e.rRule;
            this.exDate = e.exDate;
            this.exRule = e.exRule;
            this.duration = e.duration;
            this.paint = e.paint;
            this.path = e.path;
        }

        public boolean overlaps(Event e) {
            return this.startTime < e.endTime && e.startTime < this.endTime;
        }
    }

    public SparseArray<Calendar> calendars;
    public Map<String,Color> colors;
    public ArrayList<Event> events;

    public int maxLevel; // filled in by the layout algorithm as well, levels go from 0 to this, **inclusive**

    public CalendarResults() {
        // calendars = new HashMap<Integer, Calendar>();
        calendars = new SparseArray<Calendar>();
        colors = new HashMap<String, Color>();
        events = new ArrayList<Event>();
    }

    public List<WireEvent> getWireEvents() {
        List<WireEvent> wireList = new ArrayList<WireEvent>();
        for(Event e : events) {
            wireList.add(new WireEvent(e.startTime, e.endTime, e.displayColor, e.minLevel, e.maxLevel));
        }
        return wireList;
    }

    public List<EventWrapper> getWrappedEvents() {
        List<EventWrapper> wireList = new ArrayList<EventWrapper>();
        for(Event e : events) {
            wireList.add(new EventWrapper(new WireEvent(e.startTime, e.endTime, e.displayColor, e.minLevel, e.maxLevel)));
        }
        return wireList;
    }
}
