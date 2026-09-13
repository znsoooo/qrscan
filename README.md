# QR Scan（连续扫码）

本软件基于 **Camera2 + ZXing** 实现了一款原生 Android 二维码扫码工具。与一般的扫码软件不同之处在于，它可以**持续调用**摄像头连续识别二维码，并将每个二维码数据**依次添加**到结果文本末尾，可通过按钮一次复制全部结果。

需注意，两次扫码结果之间不会自动插入换行符；如需换行，请在二维码数据本身中包含换行符。

该软件不会调用网络访问，仅需获得摄像头权限。

APK文件下载：https://github.com/znsoooo/qrscan/releases

## 环境要求

- Java 17+（构建用 `javac` / `d8`）
- Android SDK：`build-tools;34.0.0` + `platforms;android-34`（由 `setup_sdk.sh` 自动安装到 `/opt/android-sdk`）
- 运行设备：Android 7.0+（系统相机框架完整）

## 构建步骤

首次构建需先安装 SDK，之后只需重新打包：

```bash
# 1) 安装 Android 构建工具链
bash setup_sdk.sh

# 2) 编译并签名打包，生成 qrscan.apk
bash build_apk.sh
```

产物位于 `./qrscan.apk`。脚本末尾会自动执行 `apksigner verify` 校验签名。

## 版权说明

- 作者：Shixian Li
- 邮箱：lsx7@sina.com
- 网址：https://github.com/znsoooo/qrscan
- 许可证：GPL v2.0
- 本软件由 WorkBuddy + Hy3 在云端虚拟机完成环境搭建和编译，经测试功能正常。如有需求改动可要求AI完成。
- 注意：本软件采用 GPL v2.0 协议，如需商用请公开修改后的源码。
