#!/bin/bash

APP_NAME=devicecloud-device-manager
JARVIS_NAME=devicecloud-devicemanager

export JAVA_HOME=/home/work/local/setup/jdk1.8.0_181
export PATH=${JAVA_HOME}/bin:${PATH}
export GRADLE_USER_HOME=~/.m2

bash ./gradlew

mv output output.src
mkdir -p output/
mkdir -p output.src/conf/
cp -rf conf.all/online-jarvis/* output.src/conf/

mv output.src/bin output.src/bin.src/

# archer3 基本结构
cp -rf jarvis/archer3-emcnginx/* output.src/
[[ -d "output.src/bin" ]] || exit 1
[[ -d "output.src/noahdes" ]] || exit 1

cp -rf output.src/bin.src/${APP_NAME} output.src/bin/
rm -rf output.src/bin.src/

cd output.src

tar zcf ../output/${JARVIS_NAME}.tar.gz *
if [[ ! -f "../output/${JARVIS_NAME}.tar.gz" ]]; then
    echo "compiler fail!"
    exit 1
fi

rm -rf ../output.src/
echo "compiler success!"

