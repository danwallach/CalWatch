package org.dwallach.calwatch;

/**
 * Created by dwallach on 12/12/14.
 */
public interface INewEvents {
    // call when there are new events loaded from the calendar
    // (0 == no new events, 1+ == that many events total)
    void newEvents(int numEvents);
}
