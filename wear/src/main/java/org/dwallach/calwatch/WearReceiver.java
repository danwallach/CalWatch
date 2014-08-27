package org.dwallach.calwatch;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireEventList;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.squareup.wire.Wire;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.wearable.Wearable.API;
import static com.google.android.gms.wearable.Wearable.DataApi;

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 *
 */
public class WearReceiver extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private final static String TAG = "WearReceiver";

    private ClockFace clockFace;

    // private List<EventWrapper> eventList = null;
    // private int maxLevel = 0;
    // private boolean showSeconds = true;
    // private int faceMode = ClockFace.FACE_TOOL;
    private GoogleApiClient mGoogleApiClient = null;
    private static WearReceiver singleton;

    public WearReceiver() {
        super();
        Log.v(TAG, "starting listening service");
        singleton = this;
    }

    public static WearReceiver getSingleton() { return singleton; }

    public void setClockFace(ClockFace clockFace) {
        this.clockFace = clockFace;
    }

    private void newEventBytes(byte[] eventBytes) {
        if(clockFace == null) {
            Log.v(TAG, "nowhere to put new events!");
            return;
        }

        Wire wire = new Wire();
        WireEventList wireEventList = null;

        try {
            wireEventList = (WireEventList) wire.parseFrom(eventBytes, WireEventList.class);
        } catch (IOException ioe) {
            Log.e(TAG, "parse failure on protobuf: " + ioe.toString());
            return;
        }

        ArrayList<EventWrapper> results = new ArrayList<EventWrapper>();

        int maxLevel = -1;
        for (WireEvent wireEvent : wireEventList.events) {
            results.add(new EventWrapper(wireEvent));

            if (wireEvent.maxLevel > maxLevel)
                maxLevel = wireEvent.maxLevel;
        }

        clockFace.setMaxLevel(maxLevel);
        clockFace.setEventList(results);
        Log.v(TAG, "new calendar event list, " + results.size() + " entries");
    }



    //
    // Official documentation: https://developer.android.com/training/wearables/data-layer/events.html
    // Very, very helpful: http://www.doubleencore.com/2014/07/create-custom-ongoing-notification-android-wear/
    //


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.v(TAG, "data changed!");

        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();

        if (!mGoogleApiClient.isConnected()) {
            Log.v(TAG, "reconnecting GoogleApiClient?!");
            ConnectionResult connectionResult = mGoogleApiClient
                    .blockingConnect(30, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "Service failed to connect to GoogleApiClient.");
                return;
            }
        }

        for (DataEvent event : events) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (Constants.WearDataPath.equals(path)) {
                    // Get the data out of the event
                    DataMapItem dataMapItem =
                            DataMapItem.fromDataItem(event.getDataItem());
                    DataMap dataMap = dataMapItem.getDataMap();

                    if(dataMap.containsKey(Constants.WearDataEvents)) {
                        byte[] eventBytes = dataMap.getByteArray(Constants.WearDataEvents);
                        newEventBytes(eventBytes);
                    }

                    if(dataMap.containsKey(Constants.WearDataShowSeconds)) {
                        boolean showSeconds = dataMap.getBoolean(Constants.WearDataShowSeconds);
                        Log.v(TAG, "showSeconds updated: " + Boolean.toString(showSeconds));
                        if(clockFace == null)
                            Log.v(TAG, "nowhere to put new showSeconds data!");
                        else
                            clockFace.setShowSeconds(showSeconds);
                    }

                    if(dataMap.containsKey(Constants.WearDataFaceMode)) {
                        int faceMode = dataMap.getInt(Constants.WearDataFaceMode);
                        Log.v(TAG, "faceMode updated: " + faceMode);

                        if(clockFace == null)
                            Log.v(TAG, "nowhere to put new faceMode data!");
                        else
                            clockFace.setFaceMode(faceMode);
                    }
                } else {
                    Log.v(TAG, "received data on weird path: "+ path);
                }
            } else {
                Log.v(TAG, "odd event type: "+ event.getType());
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "onCreate!");
        if(mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
            Log.v(TAG, "Google API connected! Hopefully.");
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.v(TAG, "onConnected!");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v(TAG, "suspended connection!");
        mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v(TAG, "lost connection!");
        mGoogleApiClient.disconnect();
        mGoogleApiClient = null;
    }

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, "+peer.getDisplayName());
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, "+peer.getDisplayName());
    }
}
