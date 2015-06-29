/*
 * CalWatch
 * Copyright (C) 2014 by Dan Wallach
 * Home page: http://www.cs.rice.edu/~dwallach/calwatch/
 * Licensing: http://www.cs.rice.edu/~dwallach/calwatch/licensing.html
 */

What's where:

notes.txt -- ongoing work, notes, to-do items, etc.

/images -- screen dumps and assorted graphics
    (note: "resample.csh", at the top level, starts from a
     high-resolution screen dump and generates preview images at all
     the correct resolutions; these downstream dependencies are all
     checked in, so unless you're changing the the icon, you don't
     need to rerun this csh script. Also note that you'll need the
     Imagemagick package installed to run it.)

/logdumps -- logcat plus notes from the various times that CalWatch has blown up

/mobile -- code that runs only on the phone
    src/androidTest/java/ -- some unit tests for event layout
    src/main/java/ -- CalWatch Java files

/shared -- code that runs on both the phone and on the watch
    src/main/java/
        EDU.Washington.grad.gjb.cassowary -- the Cassowary linear constraint solver

        - The code here is essentially unchanged from the original
          (http://sourceforge.net/projects/cassowary/), but with
          tweaks to compile under newer Java versions

        org.dwallach.calwatch/ -- CalWatch Java files

/wear -- code that runs only on the watch
