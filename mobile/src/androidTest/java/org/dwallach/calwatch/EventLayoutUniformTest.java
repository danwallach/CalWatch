package org.dwallach.calwatch;

import android.util.Log;

import junit.framework.TestCase;

import org.dwallach.calwatch.WireEvent;

import java.util.LinkedList;
import java.util.List;

public class EventLayoutUniformTest extends TestCase {
    private final static String TAG = "EventLayoutUniformTest";

    public void testGo() throws Exception {
        List<EventWrapper> data = new Builder().add(new WireEvent(1L, 10L, 1))
                .add(new WireEvent(11L, 20L, 2))
                .add(new WireEvent(21L, 30L, 3))
                .add(new WireEvent(31L, 40L, 4))
                .get();

        boolean success = EventLayoutUniform.go(data);
        assertEquals(success, true);
        levelCheck(data, EventLayoutUniform.MAXLEVEL);

        data = new Builder().add(new WireEvent(1L, 10L, 1))
                .add(new WireEvent(5L, 20L, 2))
                .add(new WireEvent(21L, 30L, 3))
                .add(new WireEvent(31L, 40L, 4))
                .get();

        success = EventLayoutUniform.go(data);
        assertEquals(success, true);
        levelCheck(data, EventLayoutUniform.MAXLEVEL);

        // okay, now for something really evil: a really long event with three stacks below it of
        // difference sizes, and another stack on the side

        data = new Builder().add(new WireEvent(1L, 30L, 1))
                .add(new WireEvent(1L, 5L, 2))
                .add(new WireEvent(11L, 15L, 3))
                .add(new WireEvent(11L, 15L, 4))
                .add(new WireEvent(21L, 25L, 5))
                .add(new WireEvent(21L, 25L, 6))
                .add(new WireEvent(21L, 25L, 7))
                .add(new WireEvent(31L, 35L, 8))
                .add(new WireEvent(31L, 35L, 9))
                .get();

        success = EventLayoutUniform.go(data);
        assertEquals(success, true);
        levelCheck(data, EventLayoutUniform.MAXLEVEL);
    }

    class Builder {
        List<EventWrapper> output;

        public Builder() {
            output = new LinkedList<EventWrapper>();
        }

        public Builder add(EventWrapper e) {
            output.add(e);
            return this;
        }

        public Builder add(WireEvent e) {
            return add(new EventWrapper(e));
        }

        public List<EventWrapper> get() {
            return output;
        }
    }

    private void levelCheck(List<EventWrapper> events, int maxLevel) {
        int nEvents = events.size();

        for (int i = 0; i < nEvents; i++) {
            EventWrapper e = events.get(i);
            assertTrue("event: " + e.toString(), e.getMinLevel() >= 0);
            assertTrue("event: " + e.toString(), e.getMaxLevel() <= maxLevel);
            Log.v(TAG, "event " + i + ": minLevel(" + e.getMinLevel() + "), maxLevel(" + e.getMaxLevel() + ")");
        }

    }
}
