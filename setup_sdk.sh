#!/bin/bash
set -e
export ANDROID_HOME=/opt/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

mkdir -p $ANDROID_HOME
cd /tmp

echo "==> download commandline-tools"
curl -sL --max-time 300 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o cmdtools.zip
unzip -q -o cmdtools.zip
mkdir -p $ANDROID_HOME/cmdline-tools
rm -rf $ANDROID_HOME/cmdline-tools/latest
mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest
echo "==> cmdline-tools ready"

echo "==> accept licenses"
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses >/dev/null 2>&1 || true

echo "==> install build-tools;34.0.0"
sdkmanager --sdk_root=$ANDROID_HOME "build-tools;34.0.0"
echo "==> install platforms;android-34"
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34"

echo "==> DONE"
ls $ANDROID_HOME/build-tools $ANDROID_HOME/platforms
