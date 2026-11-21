#!/bin/bash
set -e
ANDROID_HOME=/opt/android-sdk
BT=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34
SRC=/workspace/apk-build
LIBS=$SRC/libs

AAPT2=$BT/aapt2
D8=$BT/d8
ZIPALIGN=$BT/zipalign
APKSIGNER=$BT/apksigner
KEYSTORE=$SRC/debug.keystore
ZXING=$LIBS/zxing-core.jar

rm -rf $SRC/out $SRC/dexout $SRC/compiled $SRC/gen
mkdir -p $SRC/out $SRC/dexout $SRC/compiled $SRC/gen

echo "==> 1) aapt2 编译资源"
$AAPT2 compile --dir $SRC/res -o $SRC/compiled

echo "==> 2) aapt2 链接并生成 R.java（base.apk）"
$AAPT2 link -o $SRC/base.apk \
  -I $PLATFORM/android.jar \
  --manifest $SRC/AndroidManifest.xml \
  -R $SRC/compiled/*.flat \
  --java $SRC/gen

echo "==> 3) 编译 Java（含 R.java 与 ZXing）"
javac -cp "$PLATFORM/android.jar:$ZXING" -d $SRC/out \
  $SRC/MainActivity.java $SRC/gen/com/znsoooo/qrscan/R.java

echo "==> 4) 生成 classes.dex（含 ZXing）"
CLASSES=$(find $SRC/out -name '*.class')
$D8 --lib $PLATFORM/android.jar --output $SRC/dexout $CLASSES $ZXING

echo "==> 5) 注入 classes.dex"
( cd $SRC && zip -j base.apk dexout/classes.dex )

echo "==> 6) zipalign 对齐"
rm -f $SRC/aligned.apk
$ZIPALIGN -p 4 $SRC/base.apk $SRC/aligned.apk

echo "==> 7) 生成调试签名密钥（若不存在）"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=QRScan,O=Local"
fi

echo "==> 8) apksigner 签名"
rm -f $SRC/qrscan.apk
$APKSIGNER sign --ks "$KEYSTORE" --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --out $SRC/qrscan.apk $SRC/aligned.apk

echo "==> 完成："
ls -lh $SRC/qrscan.apk
$APKSIGNER verify $SRC/qrscan.apk && echo "签名校验通过"
