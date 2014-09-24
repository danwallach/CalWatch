set src = images/device-2014-09-23-231703.png

pngtopnm $src | pamscale -xysize 512 512 -filter=sinc | pnmtopng > images/icon-512.png
pngtopnm $src | pamscale -xysize 320 320 -filter=sinc | pnmtopng > wear/src/main/res/drawable/preview.png
pngtopnm $src | pamscale -xysize 72 72 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-hdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 48 48 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-mdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 96 96 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-xhdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 144 144 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-xxhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-hdpi/ic_launcher.png wear/src/main/res/drawable-hdpi/ic_launcher.png
cp mobile/src/main/res/drawable-mdpi/ic_launcher.png wear/src/main/res/drawable-mdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xhdpi/ic_launcher.png wear/src/main/res/drawable-xhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xxhdpi/ic_launcher.png wear/src/main/res/drawable-xxhdpi/ic_launcher.png
