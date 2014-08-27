package org.dwallach.calwatch;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import org.dwallach.calwatch.proto.WireEvent;
import org.dwallach.calwatch.proto.WireEventList;

import java.util.List;

/**
 * Created by dwallach on 8/25/14.
 */
public class WearSender implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    /*
     * Top half of this file: code for handling our watch / calendar data. Bottom half: boilerplate
     * for dealing with PlayServices and the Wear API. Guess which is longer and uglier?
     */
    public void store(List<WireEvent> wireEvents) {
        WireEventList wList = new WireEventList(wireEvents);
        byte[] wBytes = wList.toByteArray();

        Log.v("WearSender", "preparing event list for transmission, length(" + wBytes.length + " bytes)");

        makeMapRequest();
        DataMap map = mapRequest.getDataMap();
        map.putByteArray("Events", wBytes);
    }

    public void store(ClockFaceStub stub) {
        boolean showSeconds = stub.getShowSeconds();
        int faceMode = stub.getFaceMode();

        Log.v("WearSender", "preparing preferences for transmission");

        makeMapRequest();
        DataMap map = mapRequest.getDataMap();
        map.putBoolean("ShowSeconds", showSeconds);
        map.putInt("FaceMode", faceMode);
    }

    private void makeMapRequest() {
        if(mapRequest == null)
            mapRequest = PutDataMapRequest.create("/calwatch");
    }

    private PutDataMapRequest mapRequest;
    private GoogleApiClient mGoogleApiClient;
    private String nodeId;

    private static WearSender singleton = null;
    public WearSender() {
        if(singleton == null)
            singleton = this;
        onCreate();
    }

    public static WearSender getSingleton() {
        return singleton;
    }

    /*
     * Useful source: http://toastdroid.com/2014/08/18/messageapi-simple-conversations-with-android-wear/
     * Major source: https://developer.android.com/google/auth/api-client.html
     */

    public void sendNow(boolean blocking) {
        if(!isActiveConnection()) return;
        if(mapRequest == null) return;

        Log.v("WearSender", "ready to send request");

        // DataMap map = mapRequest.getDataMap();
        PutDataRequest request = mapRequest.asPutDataRequest();

        PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, request);
        if(blocking)
            pendingResult.await();

        Log.v("WearSender", "sent!");

        mapRequest = null;  // we've sent it, we can drop the reference
    }

    private boolean isActiveConnection() {
        if(readyToSend) return true;
        Log.v("WearSender", "connection inactive, retrying onCreate");
        onCreate();   // won't complete immediately, so we'll return false here
        return false;
    }

    protected void onCreate() {
        Log.v("WearSender", "onCreate!");
        // Create a GoogleApiClient instance
        // WatchCalendarService service = WatchCalendarService.getSingletonService();
        PhoneActivity activity = PhoneActivity.getSingletonActivity();
        if(activity == null) {
            Log.v("WearSender", "no activity?!");
            return;
        }
        GoogleApiClient tmp =  new GoogleApiClient.Builder(activity)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        tmp.connect();

        mGoogleApiClient = tmp; // only assign after it works
    }

    private boolean readyToSend = false;
    @Override
    public void onConnected(Bundle connectionHint) {
        Log.v("WearSender", "onConnected!");
        // Connected to Google Play services!
        // The good stuff goes here.
        readyToSend = true;
        if(mapRequest != null)
            sendNow(false);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        Log.v("WearSender", "suspended connection!");
        readyToSend = false;
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the next section.

        Log.v("WearSender", "lost connection!");
        readyToSend = false;
        mGoogleApiClient.disconnect();
    }
}
