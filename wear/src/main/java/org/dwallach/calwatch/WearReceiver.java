package org.dwallach.calwatch;

import android.net.Uri;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireEventList;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.squareup.wire.Wire;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 */
public class WearReceiver extends WearableListenerService {
    private WearActivity wearActivity;
    private List<EventWrapper> eventList = null;
    private int maxLevel = 0;

    public WearReceiver(WearActivity wearActivity) {
        this.wearActivity = wearActivity;

        // TODO: set up Data API stuff
    }

    public List<EventWrapper> getEventList() {
        return eventList;
    }

    public void go() {
        // TODO set up a new thread, etc.
    }


    public void newEventBytes(byte[] eventBytes) {
        Wire wire = new Wire();
        WireEventList wireEventList = null;

        try {
            wireEventList = (WireEventList) wire.parseFrom(eventBytes, WireEventList.class);
        } catch (IOException ioe) {
            Log.v("WearReceiver", "parse failure on protobuf: " + ioe.toString());
            return;
        }

        ArrayList<EventWrapper> results = new ArrayList<EventWrapper>();

        for(WireEvent wireEvent : wireEventList.events) {
            results.add(new EventWrapper(wireEvent));

            if(wireEvent.maxLevel > this.maxLevel)
                this.maxLevel = wireEvent.maxLevel;
        }

        eventList = results;
    }

    public int getMaxLevel() {
        return maxLevel;
    }


    //
    // below code borrowed from https://developer.android.com/training/wearables/data-layer/events.html
    // TODO fixing and integrating it and making it actually work
    //


    private static final String TAG = "DataLayerSample";
    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDataChanged: " + dataEvents);
        }
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            Uri uri = event.getDataItem().getUri();

            // Get the node id from the host value of the URI
            String nodeId = uri.getHost();
            // Set the data of the message to be the bytes of the URI.
            byte[] payload = uri.toString().getBytes();

            // Send the RPC
            Wearable.MessageApi.sendMessage(googleApiClient, nodeId,
                    DATA_ITEM_RECEIVED_PATH, payload);
        }
    }
}
