foreach dst (mobile wear)
    echo Destination: $dst
    foreach src (icon.png empty_calendar.png)
        echo -n $src
        convert $src -resize 400x400 ../$dst/src/main/res/drawable/$src:r_400.png
        echo -n .
        convert $src -resize 320x320 ../$dst/src/main/res/drawable/$src:r_preview.png
        echo -n .
        convert $src -resize 72x72 ../$dst/src/main/res/drawable-hdpi/$src:r.png
        echo -n .
        convert $src -resize 48x48 ../$dst/src/main/res/drawable-mdpi/$src:r.png
        echo -n .
        convert $src -resize 96x96 ../$dst/src/main/res/drawable-xhdpi/$src:r.png
        echo -n .
        convert $src -resize 144x144 ../$dst/src/main/res/drawable-xxhdpi/$src:r.png
        echo
    end
end


# pngtopam -alphapam $src | pamscale -xysize 512 512 -filter=sinc | pamtopng > images/icon-512.png

# set src = images/new-icon-512.png

# cp $src images/icon-512.png
# convert $src -resize 320x320 wear/src/main/res/drawable/preview.png
# convert $src -resize 72x72 mobile/src/main/res/drawable-hdpi/ic_launcher.png
# convert $src -resize 48x48 mobile/src/main/res/drawable-mdpi/ic_launcher.png
# convert $src -resize 96x96 mobile/src/main/res/drawable-xhdpi/ic_launcher.png
# convert $src -resize 144x144 mobile/src/main/res/drawable-xxhdpi/ic_launcher.png
# cp mobile/src/main/res/drawable-hdpi/ic_launcher.png wear/src/main/res/drawable-hdpi/ic_launcher.png
# cp mobile/src/main/res/drawable-mdpi/ic_launcher.png wear/src/main/res/drawable-mdpi/ic_launcher.png
# cp mobile/src/main/res/drawable-xhdpi/ic_launcher.png wear/src/main/res/drawable-xhdpi/ic_launcher.png
# cp mobile/src/main/res/drawable-xxhdpi/ic_launcher.png wear/src/main/res/drawable-xxhdpi/ic_launcher.png
