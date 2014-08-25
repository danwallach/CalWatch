package org.dwallach.calwatch;

import android.app.Activity;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

public class WearActivity extends Activity {

    private static WearActivity singletonActivity = null;

    public static WearActivity getSingletonActivity() {
        return singletonActivity;
    }

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        textOut("starting onCreate");

        singletonActivity = this;

        setContentView(R.layout.activity_wear);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);

                textOut("starting fetcher");
                CalendarFetcher fetcher = getFetcher();
                CalendarResults cr = fetcher.getContent();
            }
        });
    }

    public static void textOut(String text) {
        Log.v("WearActivity", text);
    }

    public void loadPreferences() {
        // nothing, for now
    }

    public void setClockFace(ClockFace face) {
        // nothing, for now
    }


    private CalendarFetcher fetcher = null;

    public CalendarFetcher getFetcher() {
        // nothing, for now
        if(fetcher == null) {
            fetcher = new CalendarFetcher(this);
        }
        return fetcher;
    }
}
