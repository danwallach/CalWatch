set src = images/tool-320.png

pngtopnm $src | pamscale -xysize 72 72 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-hdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 48 48 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-mdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 96 96 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-xhdpi/ic_launcher.png
pngtopnm $src | pamscale -xysize 144 144 -filter=sinc | pnmtopng > mobile/src/main/res/drawable-xxhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-hdpi/ic_launcher.png wear/src/main/res/drawable-hdpi/ic_launcher.png
cp mobile/src/main/res/drawable-mdpi/ic_launcher.png wear/src/main/res/drawable-mdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xhdpi/ic_launcher.png wear/src/main/res/drawable-xhdpi/ic_launcher.png
cp mobile/src/main/res/drawable-xxhdpi/ic_launcher.png wear/src/main/res/drawable-xxhdpi/ic_launcher.png

