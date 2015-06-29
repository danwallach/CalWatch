/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

/*
 * Portions of this file derived from Google's example code,
 * subject to the following:
 *
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

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.provider.WearableCalendarContract;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.util.Observable;
import java.util.Observer;

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
        private CalendarFetcher calendarFetcher;

        private boolean subSecondRefreshNeeded() {
            boolean result = false;

            // if the second-hand is supposed to be rendered and we're not in ambient mode
            if(clockState != null) {
                result = clockState.getShowSeconds();
            }

            if(clockFace != null) {
                result = result && !clockFace.getAmbientMode();
            }

            return result;
        }

        /**
         * Callback from ClockState if something changes, which means we'll need to redraw.
         */
        @Override
        public void update(Observable observable, Object data) {
            invalidate();
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            Log.d(TAG, "onCreate");

            super.onCreate(holder);

            // announce our version number to the logs
            VersionWrapper.logVersion(CalWatchFaceService.this);

            setWatchFaceStyle(new WatchFaceStyle.Builder(CalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.CENTER_HORIZONTAL) // not particularly precise, but seems reasonable
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_HOTWORD_INDICATOR | WatchFaceStyle.PROTECT_STATUS_BAR)

                    // the features below were added in Wear 5.1 (maybe 5.0?) and seem worth tweaking
                    .setPeekOpacityMode(WatchFaceStyle.PEEK_OPACITY_MODE_TRANSLUCENT)
                    .setShowUnreadCountIndicator(true)

                    .build());

            XWatchfaceReceiver.pingExternalStopwatches(CalWatchFaceService.this);
            BatteryWrapper.init(CalWatchFaceService.this);
            Resources resources = CalWatchFaceService.this.getResources();


            if (resources == null) {
                Log.e(TAG, "no resources? not good");
            }

            if(clockFace == null)
                clockFace = new ClockFace();

            clockState = ClockState.getSingleton();
            clockState.addObserver(this); // callbacks if something changes

            if(calendarFetcher != null)
                calendarFetcher.kill();

            calendarFetcher = new CalendarFetcher(CalWatchFaceService.this, WearableCalendarContract.Instances.CONTENT_URI, WearableCalendarContract.AUTHORITY);

            // start the background service, if it's not already running
            WearReceiverService.kickStart(CalWatchFaceService.this);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            boolean lowBitAmbientMode = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            boolean burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            clockFace.setAmbientLowBit(lowBitAmbientMode);
            clockFace.setBurnInProtection(burnInProtection);
            Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + lowBitAmbientMode + ", burn-in protection = " + burnInProtection);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
//            Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());

            // this happens exactly once per minute; we're redrawing more often than that,
            // regardless, but this also provides a backstop if something is busted or buggy,
            // so we'll keep it in.
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
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
        private int oldHeight, oldWidth;

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
//                Log.v(TAG, "onDraw");
            drawCounter++;

            int width = bounds.width();
            int height = bounds.height();

            if(width != oldWidth || height != oldHeight) {
                oldWidth = width;
                oldHeight = height;
                clockFace.setSize(width, height);
            }

            try {
                // clear the screen
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                TimeWrapper.update(); // fetch the time
                clockFace.drawEverything(canvas);
            } catch (Throwable t) {
                if(drawCounter % 1000 == 0)
                    Log.e(TAG, "Something blew up while drawing", t);
            }

            // Draw every frame as long as we're visible and doing the sweeping second hand,
            // otherwise the timer will take care of it.
            if (isVisible() && subSecondRefreshNeeded())
                invalidate();
        }

        @Override
        public void onDestroy() {
            Log.v(TAG, "onDestroy");

            if(calendarFetcher != null)
                calendarFetcher.kill();

            if(clockState != null) {
                clockState.deleteObserver(this);
                clockState = null;
            }

            if(clockFace != null) {
                clockFace.kill();
                clockFace = null;
            }

            super.onDestroy();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect bounds) {
            super.onPeekCardPositionUpdate(bounds);
            Log.d(TAG, "onPeekCardPositionUpdate: " + bounds + " (" + bounds.width() + ", " + bounds.height() + ")");

            clockFace.setPeekCardRect(bounds);

            invalidate();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            boolean isRound = insets.isRound();
            int chinSize = insets.getSystemWindowInsetBottom();

            Log.v(TAG, "onApplyWindowInsets (round: " + isRound + "), (chinSize: " + chinSize + ")");

            clockFace.setRound(isRound);
            clockFace.setMissingBottomPixels(chinSize);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);


            if (visible) {
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
