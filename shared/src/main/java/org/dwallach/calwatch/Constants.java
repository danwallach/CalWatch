/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */
package org.dwallach.calwatch;

/**
 * Created by dwallach on 8/27/14.
 */
public class Constants {
    public static final String WearDataSendPath = "/calwatchsend";
    public static final String WearDataReturnPath = "/calwatchreturn";
    public static final String WearDataEvents = "Events";
    public static final String WearDataFaceMode = "FaceMode";
    public static final int DefaultWatchFace = ClockState.FACE_TOOL;
    public static final boolean DefaultShowSeconds = true;
    public static final boolean DefaultShowDayDate = true;
}
