foreach dst (mobile wear)
    echo Destination: $dst
    set src = icon.png
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

    set src = empty_calendar.png
    echo -n $src
    convert $src -resize 60x60 ../$dst/src/main/res/drawable-hdpi/$src:r.png
    echo -n .
    convert $src -resize 40x40 ../$dst/src/main/res/drawable-mdpi/$src:r.png
    echo -n .
    convert $src -resize 80x80 ../$dst/src/main/res/drawable-xhdpi/$src:r.png
    echo -n .
    convert $src -resize 160x160 ../$dst/src/main/res/drawable-xxhdpi/$src:r.png
    echo
end
