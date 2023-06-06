#!/bin/bash
OUT="build/waffle-servant-jre"
cd $(dirname $0)
./gradlew build
MODS=$(jdeps -s build/libs/waffle-servant-all.jar | sed -e 's/.*-> //' | grep -v "not found" | grep -v "java.base" |awk 'BEGIN{s="java.base"}(""!=$1){s=s "," $1}END{print s}')
rm -rf $OUT
jlink --add-modules $MODS --compress=0 --strip-debug --no-header-files  --no-man-pages --output $OUT
cd build
tar cvf waffle-servant-jre.tar.gz waffle-servant-jre

