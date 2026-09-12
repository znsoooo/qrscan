# 连续扫码 APK

基于 **Camera2 + ZXing** 实现的原生 Android 二维码扫码工具，无网络可用。应用名为「**连续扫码**」。

## 功能特性

- 后置摄像头实时取帧，使用 ZXing 解码，对大尺寸 / 高密度二维码识别能力较高。
- 方形预览窗口，画面居中裁切。
- 持续扫描，窗口不关闭，识别到的文本追加在文本框尾部。
- 与上次识别内容完全一致时跳过。
- 预览上方弹窗显示 `识别内容: XXXX…`，其中 XXXX 为识别文本后 strip 并替换折行后取前 6 个字符。
- 结果区为只读可选取文本。
- 结果区下方提示总字数，随内容实时更新。
- 底部3个按钮：复制 / 转换 / 清空。
- 转换按钮：读取结果文本，按 `page/total:text` 分片协议重组 base64 文本并保存文件。

## 目录结构

```
apk-build/
├── .gitignore                    # 排除构建中间产物、签名密钥
├── MainActivity.java             # 核心源码：Camera2 取帧 + ZXing 解码 + UI 逻辑
├── AndroidManifest.xml           # 清单：CAMERA 权限、非全屏主题、启动 Activity
├── libs/zxing-core.jar           # ZXing 解码依赖库（构建必需，已入库）
├── res/layout/activity_main.xml  # 布局：方形预览 + 顶部弹窗 + 文本区 + 总字数 + 按钮行
├── res/drawable/*.xml            # 样式资源：预览圆角、结果框蓝边圆角、按钮蓝/浅配色、弹窗圆角
├── res/mipmap-*/ic_launcher.png  # 应用图标（mdpi ~ xxxhdpi 五档密度）
├── build_apk.sh                  # 打包脚本：aapt2 编译链接 → javac/d8 → zipalign → 签名
├── setup_sdk.sh                  # 首次构建前安装 Android SDK（build-tools;34 + platforms;android-34）
└── README.md                     # 自述文档
```

> 以下为构建产物 / 自动生成，已加入 `.gitignore` 不纳入版本库：
> `out/`、`dexout/`、`compiled/`、`gen/`、`base.apk`、`aligned.apk`、`unsigned.apk`、`*.idsig`、`debug.keystore`

## 环境要求

- Java 17+（构建用 `javac` / `d8`）
- Android SDK：`build-tools;34.0.0` + `platforms;android-34`（由 `setup_sdk.sh` 自动安装到 `/opt/android-sdk`）
- 运行设备：Android 7.0+（系统相机框架完整）

## 构建步骤

首次构建需先安装 SDK，之后只需重新打包：

```bash
# 1) 安装 Android 构建工具链（仅首次，约数百 MB，耗时几分钟）
bash setup_sdk.sh

# 2) 编译并签名打包，生成 qrscan.apk
bash build_apk.sh
```

产物位于 `./qrscan.apk`。脚本末尾会自动执行 `apksigner verify` 校验签名。

## 安装与使用

1. 将 `qrscan.apk` 传到手机，点开安装（自签名包会提示「未知来源」，允许即可）。
2. 打开 App → 点「启动摄像头扫描」→ 允许摄像头权限 → 持续识别。
3. 识别到的文本将自动追加到结果区，需要复制时点「复制」，需要清空时点「清空」。
4. 点「转换」可生成文件，如果文本符合分片协议则弹出保存选择器，不符合则气泡提示错误原因。

## 注意事项

- 若某些机型取帧格式特殊导致识别异常，可在 `MainActivity.yuv420ToNV21` 处增加兜底（如转 Bitmap 走 `RGBLuminanceSource`）。
- 转换功能需文本符合 `page/total:text,...` 分片协议，格式不符时会弹窗提示具体原因（缺失编号 / 解码失败等），成功时则弹出提示保存文件。
