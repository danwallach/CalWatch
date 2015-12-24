# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Applications/Android Studio.app/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

-keep public class org.dwallach.calwatch.PhoneActivity
-keep public class org.dwallach.calwatch.WakeupReceiver
-keep public class org.dwallach.calwatch.WatchCalendarService
-keep public class org.dwallach.calwatch.XWatchfaceReceiver
-keep public class org.dwallach.calwatch.proto.*

# -keep class com.squareup.wire.** { *; }

-keep public class android.support.**
-keep public class android.app.**
-keep public class android.R**

# -dontwarn okio.**

-dontobfuscate