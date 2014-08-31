package org.dwallach.calwatch;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireUpdate;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.squareup.wire.Wire;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class pairs up with WearSender
 * Created by dwallach on 8/25/14.
 *
 */
public class WearReceiverService extends WearableListenerService {
    private final static String TAG = "WearReceiver";

    // private List<EventWrapper> eventList = null;
    // private int maxLevel = 0;
    // private boolean showSeconds = true;
    // private int faceMode = ClockFace.FACE_TOOL;
    private GoogleApiClient mGoogleApiClient = null;
    private static WearReceiverService singleton;

    public WearReceiverService() {
        super();
        Log.v(TAG, "starting listening service");
        singleton = this;
    }

    public static WearReceiverService getSingleton() { return singleton; }

    private void newEventBytes(byte[] eventBytes) {
        ClockFace clockFace;
        try {
            clockFace = WearActivity.getSingletonActivity().getViewAnim().getClockFace();
            if(clockFace == null) {
                Log.v(TAG, "nowhere to put new events!");
                return;
            }
        } catch (NullPointerException e) {
            Log.v(TAG, "something's not ready for a clock face: " + e.toString());
            return;
        }

        Wire wire = new Wire();
        WireUpdate wireUpdate = null;

        try {
            wireUpdate = (WireUpdate) wire.parseFrom(eventBytes, WireUpdate.class);
        } catch (IOException ioe) {
            Log.e(TAG, "parse failure on protobuf: " + ioe.toString());
            return;
        }

        if(wireUpdate.newEvents) {
            ArrayList<EventWrapper> results = new ArrayList<EventWrapper>();

            int maxLevel = -1;
            for (WireEvent wireEvent : wireUpdate.events) {
                results.add(new EventWrapper(wireEvent));

                if (wireEvent.maxLevel > maxLevel)
                    maxLevel = wireEvent.maxLevel;
            }

            clockFace.setMaxLevel(maxLevel);
            clockFace.setEventList(results);
            Log.v(TAG, "new calendar event list, " + results.size() + " entries");
        }

        clockFace.setFaceMode(wireUpdate.faceMode);
        clockFace.setShowSeconds(wireUpdate.showSeconds);
        Log.v(TAG, "event update complete");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v("WearReceiver", "service starting!");
        // handleCommand(intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.

        // TODO: send message to wake up phone and update us here on the watch
        // Note that we're doing is here and not in onCreate(). This seems
        // to get called later on, and suggests that we'll be ready to receive
        // a message in return from the phone, assuming it's alive and kicking.

        pingPhone();

        return START_STICKY;
    }

    //
    // Official documentation: https://developer.android.com/training/wearables/data-layer/events.html
    // Very, very helpful: http://www.doubleencore.com/2014/07/create-custom-ongoing-notification-android-wear/
    //

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.v(TAG, "message received!");
        initGoogle();
        if (messageEvent.getPath().equals(Constants.WearDataSendPath)) {
            newEventBytes(messageEvent.getData());
        } else {
            Log.v(TAG, "received message on unexpected path: " + messageEvent.getPath());
        }
    }

    private void initGoogle() {
        if(mGoogleApiClient == null)
            mGoogleApiClient = new GoogleApiClient.Builder(this).
                    addApi(Wearable.API).
                    build();
        if (!mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
        if (!mGoogleApiClient.isConnected()) {
            Log.e(TAG, "not connected to GoogleAPI?");
        } else {
            Log.e(TAG, "connected to GoogleAPI!");
        }
    }

        @Override
    public void onCreate() {
        super.onCreate();

        Log.v(TAG, "onCreate!");
        initGoogle();
    }

    /*
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
    */

    public void onPeerConnected(Node peer) {
        Log.v(TAG, "phone is connected!, "+peer.getDisplayName());

        pingPhone();
    }

    public void onPeerDisconnected(Node peer) {
        Log.v(TAG, "phone is disconnected!, "+peer.getDisplayName());
    }

    public void pingPhone() {
        Log.v(TAG, "pinging phone for data");

        if (mGoogleApiClient.isConnected()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    int failures = 0;
                    for (Node node : nodes.getNodes()) {
                        Log.v(TAG, "Sending to node: " + node.getDisplayName());
                        MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(
                                mGoogleApiClient, node.getId(), Constants.WearDataReturnPath, null).await();
                        if (!result.getStatus().isSuccess()) {
                            Log.e(TAG, "ERROR: failed to send Message: " + result.getStatus());
                            failures++;
                        }
                        if (failures == 0) {
                            Log.v(TAG, "ping delivered!");
                        }
                    }

                    return null;
                }
            }.execute();
        }
    }
}
