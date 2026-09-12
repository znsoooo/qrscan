package com.example.qrscan;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final int REQ_CAM = 1;
    private static final long DECODE_INTERVAL_MS = 120; // 解码节流
    private static final int MAX_W = 1920;             // 取帧分辨率上限（大码也能识别）

    private TextureView textureView;
    private TextView overlay;
    private TextView result;
    private TextView tvCount;
    private ScrollView scrollResult;
    private FrameLayout previewContainer;

    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader imageReader;
    private Size previewSize;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private final QRCodeReader qrReader = new QRCodeReader();
    private String lastRaw = null;
    private long lastDecodeTime = 0;
    private final Runnable hideOverlay = new Runnable() {
        @Override
        public void run() {
            overlay.setVisibility(View.INVISIBLE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 沉浸式浅色状态栏：与应用底色一致，时间/图标用深色
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#f2f3f5"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        previewContainer = findViewById(R.id.previewContainer);
        textureView = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        result = findViewById(R.id.result);
        scrollResult = findViewById(R.id.scrollResult);
        tvCount = findViewById(R.id.tvCount);

        // 让预览画面按背景圆角剪裁
        previewContainer.setClipToOutline(true);

        // 预览容器裁成正方形
        previewContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        int w = previewContainer.getWidth();
                        if (w > 0 && previewContainer.getHeight() != w) {
                            previewContainer.getLayoutParams().height = w;
                            previewContainer.requestLayout();
                        }
                    }
                });

        Button btnCopy = findViewById(R.id.btnCopy);
        Button btnConvert = findViewById(R.id.btnConvert);
        Button btnClear = findViewById(R.id.btnClear);

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = result.getText().toString();
                if (text.isEmpty()) {
                    Toast.makeText(MainActivity.this, "文本框为空，无可复制内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("qr", text));
                Toast.makeText(MainActivity.this, "已复制 " + text.length() + " 个字符", Toast.LENGTH_SHORT).show();
            }
        });

        btnConvert.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: 转换功能待实现
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.setText("");
                lastRaw = null;
                updateCount();
                Toast.makeText(MainActivity.this, "已清空", Toast.LENGTH_SHORT).show();
            }
        });

        textureView.setSurfaceTextureListener(surfaceTextureListener);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        if (requestCode == REQ_CAM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (textureView.isAvailable()) {
                    openCamera(textureView.getWidth(), textureView.getHeight());
                }
            } else {
                Toast.makeText(this, "未授予摄像头权限，无法扫码", Toast.LENGTH_LONG).show();
            }
        }
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    openCamera(w, h);
                }
                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
                    configureTransform(w, h);
                }
                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    return true;
                }
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture st) {
                }
            };

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void openCamera(int width, int height) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics ch = cameraManager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
            CameraCharacteristics ch = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(ImageReader.class);
            previewSize = chooseLargest(sizes, MAX_W);

            imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    android.graphics.ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(onImageAvailable, backgroundHandler);

            cameraManager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "摄像头打开失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Size chooseLargest(Size[] sizes, int maxW) {
        Size best = null;
        for (Size s : sizes) {
            if (s.getWidth() <= maxW && (best == null || s.getWidth() > best.getWidth())) {
                best = s;
            }
        }
        if (best == null) {
            for (Size s : sizes) {
                if (best == null || s.getWidth() > best.getWidth()) best = s;
            }
        }
        return best;
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createPreviewSession();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "摄像头初始化错误", Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private void createPreviewSession() {
        try {
            SurfaceTexture st = textureView.getSurfaceTexture();
            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(st);
            Surface readerSurface = imageReader.getSurface();

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.addTarget(readerSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, readerSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                                        CameraMetadata.CONTROL_MODE_AUTO);
                                session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        configureTransform(textureView.getWidth(), textureView.getHeight());
                                    }
                                });
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "预览配置失败", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewW, int viewH) {
        if (textureView == null || previewSize == null || viewW == 0 || viewH == 0) return;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();

        // 屏幕旋转到 90/270 时，buffer 需按旋转后的宽高处理
        boolean rotated = (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270);
        float bufW = rotated ? previewSize.getHeight() : previewSize.getWidth();
        float bufH = rotated ? previewSize.getWidth() : previewSize.getHeight();

        float cx = viewW / 2f;
        float cy = viewH / 2f;

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewW, viewH);
        RectF bufferRect = new RectF(0, 0, bufW, bufH);
        bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY());

        // 先抵消 TextureView 的默认拉伸，再等比放大到覆盖控件（center-crop：铺满、无黑边、不变形）
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(viewH / bufH, viewW / bufW);
        matrix.postScale(scale, scale, cx, cy);
        if (rotated) {
            matrix.postRotate(90 * (rotation - 2), cx, cy);
        }
        textureView.setTransform(matrix);
    }

    private final ImageReader.OnImageAvailableListener onImageAvailable =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireLatestImage();
                    if (image == null) return;
                    long now = System.currentTimeMillis();
                    if (now - lastDecodeTime < DECODE_INTERVAL_MS) {
                        image.close();
                        return;
                    }
                    lastDecodeTime = now;
                    try {
                        byte[] nv21 = yuv420ToNV21(image);
                        int w = image.getWidth();
                        int h = image.getHeight();
                        PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                                nv21, w, h, 0, 0, w, h, false);
                        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));
                        try {
                            Result res = qrReader.decode(bitmap);
                            handleResult(res.getText());
                        } catch (Exception e) {
                            // 当前帧无二维码或解码失败，忽略
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        image.close();
                    }
                }
            };

    private void handleResult(final String text) {
        final String display = buildOverlayText(text);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlay.setText("识别内容: " + display);
                overlay.setVisibility(View.VISIBLE);
                overlay.removeCallbacks(hideOverlay);
                overlay.postDelayed(hideOverlay, 2500);

                if (lastRaw != null && text.equals(lastRaw)) {
                    return; // 与上次完全一致，跳过追加
                }
                lastRaw = text;
                result.append(text); // 不 strip、不加换行，直接追加
                updateCount();
                scrollResult.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollResult.fullScroll(View.FOCUS_DOWN); // 滚动到最底部
                    }
                });
            }
        });
    }

    // 弹窗文案：strip → 连续空白折叠为一个空格 → 取前 6 个字符 + 省略号
    private String buildOverlayText(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        String head = normalized.length() > 6 ? normalized.substring(0, 6) : normalized;
        return head + "…";
    }

    // 更新“总字数：XXX”
    private void updateCount() {
        tvCount.setText("总字数：" + result.getText().length());
    }

    // 将 YUV_420_888 转为 NV21（ZXing PlanarYUVLuminanceSource 所需布局）
    private byte[] yuv420ToNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuf = planes[0].getBuffer();
        ByteBuffer uBuf = planes[1].getBuffer();
        ByteBuffer vBuf = planes[2].getBuffer();
        int yRowStride = planes[0].getRowStride();
        int yPixStride = planes[0].getPixelStride();
        int uRowStride = planes[1].getRowStride();
        int uPixStride = planes[1].getPixelStride();
        int vRowStride = planes[2].getRowStride();
        int vPixStride = planes[2].getPixelStride();

        byte[] nv21 = new byte[width * height + width * (height / 2)];
        int ySize = width * height;

        // Y 平面
        if (yRowStride == width && yPixStride == 1) {
            yBuf.get(nv21, 0, ySize);
        } else {
            for (int row = 0; row < height; row++) {
                yBuf.position(row * yRowStride);
                yBuf.get(nv21, row * width, width);
            }
        }

        // VU 交错（NV21：先 V 后 U）。UV 平面每行列仅 width/2 个采样点
        int uvHeight = height / 2;
        int uvWidth = width / 2;
        for (int row = 0; row < uvHeight; row++) {
            int yRowBase = ySize + row * width;
            int uRowPos = row * uRowStride;
            int vRowPos = row * vRowStride;
            for (int col = 0; col < uvWidth; col++) {
                int out = yRowBase + 2 * col;
                nv21[out]     = vBuf.get(vRowPos + col * vPixStride); // V
                nv21[out + 1] = uBuf.get(uRowPos + col * uPixStride); // U
            }
        }
        return nv21;
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}
