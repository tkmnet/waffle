#!/bin/sh

if [ ! -e "./lib/jdk-*" ];then
  sh ./lib/extend_jdk.sh
fi
cd lib/jdk-*/bin
JAVA_PATH="$(pwd)"
cd ../../../
PATH="${JAVA_PATH}:${PATH}" ./gradlew build
cp build/libs/waffle-all.jar WAFFLE/.INTERNAL/

