package org.dwallach.calwatch;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireEventList;

import java.util.List;

/**
 * Created by dwallach on 8/25/14.
 */
public class WearSender {
    public void send(List<WireEvent> wireEvents) {
        WireEventList wList = new WireEventList(wireEvents);
        byte[] wBytes = wList.toByteArray();

        // TODO: do something useful
    }

    public void sendPrefs(ClockFaceStub stub) {
        boolean showSeconds = stub.getShowSeconds();
        int faceMode = stub.getFaceMode();

        // TODO: do something useful
    }
}
