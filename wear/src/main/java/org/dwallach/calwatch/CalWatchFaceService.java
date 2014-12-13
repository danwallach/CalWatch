package org.dwallach.calwatch;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.SurfaceHolder;


public class CalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "CalWatchFaceService";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    public class Engine extends CanvasWatchFaceService.Engine {

        static final int BACKGROUND_COLOR = Color.BLACK;
        static final int FOREGROUND_COLOR = Color.WHITE;
        static final int TEXT_SIZE = 25;
        static final int MSG_LOAD_MEETINGS = 0;

        /** Editable string containing the text to draw with the number of meetings in bold. */
        final Editable mEditable = new SpannableStringBuilder();

        /** Width specified when {@link #mLayout} was created. */
        int mLayoutWidth;

        /** Layout to wrap {@link #mEditable} onto multiple lines. */
        DynamicLayout mLayout;

        /** Paint used to draw text. */
        final TextPaint mTextPaint = new TextPaint();

        int mNumMeetings;

        private CalendarFetcher calendarFetcher;

        private AsyncTask<Void, Void, Integer> calendarFetcherTask;

        /** Handler to load the meetings once a minute in interactive mode. */
        final Handler mLoadMeetingsHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_LOAD_MEETINGS:
                        cancelCalendarFetcherTask();
                        calendarFetcherTask = new LoadMeetingsTask();
                        calendarFetcherTask.execute();
                        break;
                }
            }
        };

        private boolean mIsReceiverRegistered;

        private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_PROVIDER_CHANGED.equals(intent.getAction())
                        && WearableCalendarContract.CONTENT_URI.equals(intent.getData())) {
                    CalendarFetcher
                    cancelCalendarFetcherTask();
                    mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(CalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mTextPaint.setColor(FOREGROUND_COLOR);
            mTextPaint.setTextSize(TEXT_SIZE);

            mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
        }

        @Override
        public void onDestroy() {
            mLoadMeetingsHandler.removeMessages(MSG_LOAD_MEETINGS);
            cancelCalendarFetcherTask();
            super.onDestroy();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Create or update mLayout if necessary.
            if (mLayout == null || mLayoutWidth != bounds.width()) {
                mLayoutWidth = bounds.width();
                mLayout = new DynamicLayout(mEditable, mTextPaint, mLayoutWidth,
                        Layout.Alignment.ALIGN_NORMAL, 1 /* spacingMult */, 0 /* spacingAdd */,
                        false /* includePad */);
            }

            // Update the contents of mEditable.
            mEditable.clear();
//            mEditable.append(Html.fromHtml(getResources().getQuantityString(
//                    R.plurals.calendar_meetings, mNumMeetings, mNumMeetings)));

            // Draw the text on a solid background.
            canvas.drawColor(BACKGROUND_COLOR);
            mLayout.draw(canvas);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                IntentFilter filter = new IntentFilter(Intent.ACTION_PROVIDER_CHANGED);
                filter.addDataScheme("content");
                filter.addDataAuthority(WearableCalendarContract.AUTHORITY, null);
                registerReceiver(mBroadcastReceiver, filter);
                mIsReceiverRegistered = true;

                mLoadMeetingsHandler.sendEmptyMessage(MSG_LOAD_MEETINGS);
            } else {
                if (mIsReceiverRegistered) {
                    unregisterReceiver(mBroadcastReceiver);
                    mIsReceiverRegistered = false;
                }
                mLoadMeetingsHandler.removeMessages(MSG_LOAD_MEETINGS);
            }
        }

        private void onMeetingsLoaded(Integer result) {
            if (result != null) {
                mNumMeetings = result;
                invalidate();
            }
        }

        private void cancelCalendarFetcherTask() {
            if (calendarFetcherTask != null) {
                calendarFetcherTask.cancel(true);
            }
        }
    }
}
