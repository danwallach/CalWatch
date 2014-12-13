/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dwallach.calwatch;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.util.Observable;
import java.util.Observer;
import java.util.TimeZone;

/**
 * Drawn heavily from the Android Wear SweepWatchFaceService example code.
 */
public class CalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "CalWatchFaceService";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements Observer {
        private ClockFace clockFace;
        private ClockState clockState;

        /**
         * Callback from ClockState if something changes, which means we'll need
         * to redraw.
         * @param observable
         * @param data
         */
        @Override
        public void update(Observable observable, Object data) {
            invalidate();
        }

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean lowBitAmbientMode;
        boolean muteMode;


        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            // announce our version number to the logs
            VersionWrapper.logVersion(CalWatchFaceService.this);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.CENTER)
                    .build());

            BatteryWrapper.init(CalWatchFaceService.this);
            Resources resources = CalWatchFaceService.this.getResources();

            if (resources == null) {
                Log.e(TAG, "no resources? not good");
            }

            clockFace = new ClockFace();
            clockState = ClockState.getSingleton();

            clockState.addObserver(this); // callbacks if something changes

            // initialize the thing that will bother the user if we can't see the phone
            WearNotificationHelper.init(true,
                    R.drawable.ic_launcher,
                    resources.getString(R.string.nophone_title),
                    resources.getString(R.string.nophone_text));


            // start the background service, if it's not already running
            WearReceiverService.kickStart(CalWatchFaceService.this);

        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbientMode = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            clockFace.setAmbientLowBit(lowBitAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + lowBitAmbientMode);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            clockFace.setAmbientMode(inAmbientMode);

            // If we just switched *to* ambient mode, then we've got some FPS data to report
            // to the logs. Otherwise, we're coming *back* from ambient mode, so it's a good
            // time to reset the counters.
            if(inAmbientMode)
                TimeWrapper.frameReport();
            else
                TimeWrapper.frameReset();

            invalidate();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            clockFace.setMuteMode(interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            invalidate();
        }

        private long drawCounter = 0;
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
//            if (Log.isLoggable(TAG, Log.VERBOSE)) {
//                Log.v(TAG, "onDraw");
//            }
            drawCounter++;

            int width = bounds.width();
            int height = bounds.height();

            try {
                // clear the screen
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                clockFace.setSize(width, height);

                TimeWrapper.update(); // fetch the time
                clockFace.drawEverything(canvas);
            } catch (Throwable t) {
                if(drawCounter % 1000 == 0)
                    Log.e(TAG, "Something blew up while drawing", t);
            }

            // Draw every frame as long as we're visible and in interactive mode.
            if (isVisible() && !isInAmbientMode() && ClockState.getSingleton().getShowSeconds()) {
                invalidate();
            }

        }

        @Override
        public void onPeekCardPositionUpdate(Rect bounds) {
            super.onPeekCardPositionUpdate(bounds);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPeekCardPositionUpdate: " + bounds);
            }

            // TODO: do something with the peek card bounds

            invalidate();
        }


        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);


            if (visible) {
                // We might need to yell at the user to run the phone-side of the app
                // so it can send us calendar data. We don't want to do this check
                // during the onDraw loop, but it's fine here, since this only happens
                // when our watchface becomes visibile -- a perfectly reasonable time
                // to do a notification.
                WearNotificationHelper.maybeNotify(CalWatchFaceService.this);
                invalidate();
            }

            // If we just switched *to* not visible mode, then we've got some FPS data to report
            // to the logs. Otherwise, we're coming *back* from invisible mode, so it's a good
            // time to reset the counters.
            if(!visible)
                TimeWrapper.frameReport();
            else
                TimeWrapper.frameReset();

        }
    }
}
