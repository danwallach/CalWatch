![CalWatch2](images/feature-graphic-sm.png)

* Copyright © 2014-2019 by Dan S. Wallach
* Home page: http://www.cs.rice.edu/~dwallach/calwatch/
* Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html

## tl;dr

CalWatch2 is an open-source WearOS watchface, compatible with WearOS 1.x and 2.x, that reads your calendar
and renders the next twelve hours of your schedule around the face of the watch. CalWatch2
is mostly implemented in the Kotlin programming language.

## What's where

* notes.txt -- ongoing work, notes, to-do items, etc.

* /images -- screen dumps and assorted graphics

    * note: "resample.csh", at the top level, starts from a
      high-resolution screen dump and generates preview images at all
      the correct resolutions; these downstream dependencies are all
      checked in, so unless you're changing the the icon, you don't
      need to rerun this csh script. Also note that you'll need the
      Imagemagick package installed to run it.)

    * For most things, I'm now using the vector / scalable image types
      supported by Android. You'll find PDFs (drawn in Illustrator) here,
      and SVG output from that, which is then digestible by Android Studio.

* /logdumps -- logcat plus notes from the various times that CalWatch has blown up

* /app -- the unifed app that runs on Wear watches
    * src/androidTest/java/ -- some old unit tests for event layout
    * src/main/kotlin/ -- CalWatch source-code files
        * org.dwallach.calwatch2/ -- CalWatch Kotlin files
        * org.dwallach.complications/ -- based on the Android sample code and heavily modified

    * src/main/java/EDU.Washington.grad.gjb.cassowary -- the Cassowary linear constraint solver
        * The code here is essentially unchanged from the original
          (http://sourceforge.net/projects/cassowary/), but with
          tweaks to compile under newer Java versions, including
          updates to use the newer parametric HashMap versus the
          older non-parametric Hashtable.


## History
The original version of CalWatch was engineered for the original version of Android Wear, which
originally shipped without any documented support for Android Wear watchfaces. You can see
this in the [release1 code](https://github.com/danwallach/CalWatch/tree/release1) from November 2014.

Then, Google announced decent APIs for building watchfaces, which did all the hard work for you.
You can see that in [release2](https://github.com/danwallach/CalWatch/tree/release2)

For [release3](https://github.com/danwallach/CalWatch/tree/release3), I added in support for an
external stopwatch and timer app that would send messages to CalWatch to display their state on the
watchface. (This was before Android Wear supported complications.) Also, in the earlier versions
I was reading calendar events on the phone and sending them over to the watch. By this time, the watch
finally had a working local calendar database, greatly simplifying things.

For [release4](https://github.com/danwallach/CalWatch/tree/release4), I ported everything to
Kotlin. I wrote up [the engineering process](https://discuss.kotlinlang.org/t/experience-porting-an-android-app-to-kotlin/1399)
on the Kotlin discussion board.

For [release5](https://github.com/danwallach/CalWatch/tree/release5), I rewrote the phone-side
configuration panel, using all the newest Material Design style.

Shortly thereafter, at Google I/O 2016, Google announced WearOS 2 which, of course,
completely changed how the phone and the watch relate to one another,
making it easier for a WearOS watch to interoperate with an iOS phone, but killing off the
idea that every Wear app is embedded in an Android phone app. Fast forward to I/O 2017 and
Google announced they were providing sample code and libraries to make it much easier to support
complications, plus they began rolling out Wear 2 to more watches. If you rummage through the
commit history, you'll see that I had the watch running with complications, but without a particularly
usable configuration UI on the watch.

I finally got everything working and pushed proper Wear1.x and 2.x-compatible APKs to the Play Store
in December 2018/2019. The current release also uses the latest
Kotlin coroutine support to do the expensive calendar processing asynchronously, and has
a bunch of other small tweaks to use more of the Kotlin stdlib features that didn't exist
in earlier Kotlin releases. There seems to be [a bug in how we get notified about calendar
changes](https://issuetracker.google.com/issues/122149553) that impacts the Android 8.1 Oreo variant
of WearOS that's running on WearOS devices, but it seems to be fixed in the "Wear H" variant
based on Android 9.0.

## Credit where credit is due:

* Cassowary linear constraint solver by Greg J. Badros and Alan Borning
  https://constraints.cs.washington.edu/cassowary/

* "Unavailable calendar" icon by Björn Andersson
  ttp://www.flaticon.com/authors/google
