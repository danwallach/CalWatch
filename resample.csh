set src = images/new-icon-512.png

# pngtopam -alphapam $src | pamscale -xysize 512 512 -filter=sinc | pamtopng > images/icon-512.png
cp $src images/icon-512.png
convert $src -size 320x320 wear/src/main/res/drawable/preview.png
convert $src -size 72x72 mobile/src/main/res/drawable-hdpi/ic_launcher.png
convert $src -size 48x48 mobile/src/main/res/drawable-mdpi/ic_launcher.png
convert $src -size 96x96 mobile/src/main/res/drawable-xhdpi/ic_launcher.png
convert $src -size 144x144 mobile/src/main/res/drawable-xxhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-hdpi/ic_launcher.png wear/src/main/res/drawable-hdpi/ic_launcher.png
cp mobile/src/main/res/drawable-mdpi/ic_launcher.png wear/src/main/res/drawable-mdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xhdpi/ic_launcher.png wear/src/main/res/drawable-xhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xxhdpi/ic_launcher.png wear/src/main/res/drawable-xxhdpi/ic_launcher.png
