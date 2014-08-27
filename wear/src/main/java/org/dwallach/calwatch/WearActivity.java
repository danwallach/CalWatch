package org.dwallach.calwatch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;

public class WearActivity extends Activity {

    private static WearActivity singletonActivity = null;
    private WearReceiver receiver;

    public static WearActivity getSingletonActivity() {
        return singletonActivity;
    }

    private MyViewAnim view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v("WearActivity", "starting onCreate");

        singletonActivity = this;

        // start the calendar service, if it's not already running
        receiver = WearReceiver.getSingleton();

        if(receiver == null) {
            Intent serviceIntent = new Intent(this, WearReceiver.class);
            startService(serviceIntent);

            // do it again; we should get something different this time
            receiver = WearReceiver.getSingleton();
        }

        // bring up the UI

        setContentView(R.layout.activity_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                view = (MyViewAnim) stub.findViewById(R.id.surfaceView);

                Log.v("WearActivity", "starting data API receiver");
            }
        });
    }

    public void loadPreferences() {
        // nothing, for now
    }

    public void setClockFace(ClockFace face) {
        if(receiver == null)
            Log.v("WearActivity", "no receiver running yet!");
        else
            receiver.setClockFace(face);
    }
}
