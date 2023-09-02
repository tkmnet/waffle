#!/bin/sh

JRE_URL="https://files.tkms.jp/waffle-jre.tar.gz"
JAR_URL="https://desk.tkms.jp/resource/WjjMKKgC2MdI4GpepkzGOy4yQ3dWEOxM/waffle-all.jar"
JAVA_OPT="--add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED"

export WAFFLE_SH="$0"

WAFFLE_INTERNAL="WAFFLE/.INTERNAL"
START_FLAG=${WAFFLE_INTERNAL}/AUTO_START
REMOVE_FLAG=${WAFFLE_INTERNAL}/REMOVE_JAR
mkdir -p $WAFFLE_INTERNAL

JAR_FILE="${WAFFLE_INTERNAL}/waffle-all.jar"
JRE_FILE="${WAFFLE_INTERNAL}/waffle-jre.tar.gz"
JRE_DIR="${WAFFLE_INTERNAL}/waffle-jre"

if [ "$1" = "-u" ];then
  rm $JAR_FILE >/dev/null 2>&1
elif [ "$1" = "-U" ];then
  rm $JAR_FILE >/dev/null 2>&1
  rm $JRE_FILE >/dev/null 2>&1
  rm -rf $JRE_DIR >/dev/null 2>&1
fi

if [ -n "${JAVA_HOME}" ];then
  JAVA="${JAVA_HOME}/bin/java"
else
  JAVA="${JRE_DIR}/bin/java"
fi

if which xdg-open >/dev/null 2>&1; then
  export WAFFLE_OPEN_COMMAND="xdg-open"
fi

touch $START_FLAG
while [ -e "$START_FLAG" ];do

  if [ ! -e "$JAR_FILE" ];then
    if [ -e "waffle-all.jar" ];then
      cp waffle-all.jar $JAR_FILE
    else
      if which curl >/dev/null 2>&1; then
        curl -o "$JAR_FILE" "$JAR_URL"
      else
        if which wget >/dev/null 2>&1; then
          wget -O "$JAR_FILE" "$JAR_URL"
        else
          echo "Please download manually waffle-all.jar because cURL and Wget are not available."
          exit 1;
        fi
      fi
    fi
  fi

  if [ ! -e "$JRE_DIR/bin/java" ];then
    if [ -e "$JRE_FILE" ];then
      sh -c "cd ${WAFFLE_INTERNAL} && tar xf waffle-jre.tar.gz"
    else
      if [ -e "waffle-jre.tar.gz" ];then
        cp waffle-jre.tar.gz $JRE_FILE
        sh -c "cd ${WAFFLE_INTERNAL} && tar xf waffle-jre.tar.gz"
      else
        if which curl >/dev/null 2>&1; then
          curl -o "$JRE_FILE" "$JRE_URL"
          sh -c "cd ${WAFFLE_INTERNAL} && tar xf waffle-jre.tar.gz"
        else
          if which wget >/dev/null 2>&1; then
            wget -O "$JRE_FILE" "$JRE_URL"
            sh -c "cd ${WAFFLE_INTERNAL} && tar xf waffle-jre.tar.gz"
          else
            echo "Please download manually waffle-jre.tar.gz because cURL and Wget are not available."
            exit 1;
          fi
        fi
      fi
    fi
  fi

  rm -f $START_FLAG
  ${JAVA} ${JAVA_OPT} -jar ${JAR_FILE}

  if [ -e "$REMOVE_FLAG" ];then
    rm $REMOVE_FLAG >/dev/null 2>&1
    rm $JAR_FILE >/dev/null 2>&1
  fi
done

