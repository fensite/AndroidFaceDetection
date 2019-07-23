package com.fensite.facetrace;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * create by zhaikn on 2019/7/18
 */
public class DisplaySurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private final String TAG = "display";
    private Handler mCameraHandler;
    private HandlerThread handlerThread = new HandlerThread("CameraThread");

    private final int DEFAULT_WIDTH = 640;
    private final int DEFAULT_HEIGHT = 480;

    private int width;
    private int height;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private SurfaceTexture mSurfaceTexture = null;
    private String mCameraId;
    private int currentCameraId;
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;


    private Point maxPreviewSize;
    private Point minPreviewSize;
    private Size mPreviewSize;
    private Point previewViewSize;
    private Point specificPreviewSize;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;


    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            int rotation = ((Activity) getContext()).getWindowManager().getDefaultDisplay()
                    .getRotation();
            Image image = reader.acquireLatestImage();
            //我们可以将这帧数据转成字节数组，类似于Camera1的PreviewCallback回调的预览帧数据
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            TraceHelper.detectorFace(data, width, height, rotation, mCameraId);
            buffer.get(data);
            image.close();

        }
    };

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            if (null == mCameraDevice)
                return;

            try {
                CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                builder.addTarget(mImageReader.getSurface());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            Log.e("displaySurface", " 打开摄像头失败");
        }
    };

    public DisplaySurfaceView(Context context) {
        super(context);
        getHolder().addCallback(this);
    }

    public DisplaySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    public DisplaySurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    /**
     * 切换摄像头
     */
    public void switchCamera() {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                Size maxSize = getMaxSize(map.getOutputSizes(SurfaceHolder.class));
                if (currentCameraId == CameraCharacteristics.LENS_FACING_BACK && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    //前置转后置
//                    previewSize = maxSize;
                    currentCameraId = CameraCharacteristics.LENS_FACING_FRONT;
                    stopPreview();
                    startPreview();
                    break;
                } else if (currentCameraId == CameraCharacteristics.LENS_FACING_FRONT && characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                    //后置转前置
//                    previewSize = maxSize;
                    currentCameraId = CameraCharacteristics.LENS_FACING_BACK;
                    stopPreview();
                    startPreview();
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        try {
            //获取摄像头管理
            CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIdList = cameraManager.getCameraIdList();//获取可用相机列表
            Log.e("trace", "可用相机的个数是:" + cameraIdList.length);
            if (cameraIdList.length <= 0) {
                Log.e(TAG, "无可用相机");
                return;
            }
            String cameraId = cameraIdList[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);//获取某个相机(摄像头特性)
            int supportLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);//检查支持
            if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                Log.w(TAG, "硬件不支持新特性");
            }

            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                return;
            }
            mPreviewSize = getBestSupportedSize(new ArrayList<Size>(Arrays.asList(map.getOutputSizes(SurfaceTexture.class))));
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(
                    new OnImageAvailableListenerImpl(), null);



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (getContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    Activity#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for Activity#requestPermissions for more details.
                    return;
                }
            }

            cameraManager.openCamera(mCameraId, mStateCallback, null);
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(getHolder().getSurface());
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);     // 闪光灯
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);// 自动对焦

            // For still image captures, we use the largest available size.
            Size largest = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    new CompareSizesByArea());
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);

            mCameraDevice.createCaptureSession(Arrays.asList(getHolder().getSurface(), mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "set ok");
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }
                    mCameraCaptureSession = session;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(builder.build(), mCaptureCallback, mBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "开启预览会话失败!");
                }
            }, null);

        } catch (Exception e) {
            Log.e("trace", "Exception");
        }

        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                ImageFormat.YUV_420_888, 2);



    }

    private void stopPreview() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

    }


    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(TAG,"onCaptureCompleted");
            result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG,"onCaptureFailed");
        }
    };

    private Size getBestSupportedSize(List<Size> sizes) {
        Size defaultSize = sizes.get(0);
        Size[] tempSizes = sizes.toArray(new Size[0]);
        Arrays.sort(tempSizes, new Comparator<Size>() {
            @Override
            public int compare(Size o1, Size o2) {
                if (o1.getWidth() > o2.getWidth()) {
                    return -1;
                } else if (o1.getWidth() == o2.getWidth()) {
                    return o1.getHeight() > o2.getHeight() ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        sizes = new ArrayList<>(Arrays.asList(tempSizes));
        for (int i = sizes.size() - 1; i >= 0; i--) {
            if (maxPreviewSize != null) {
                if (sizes.get(i).getWidth() > maxPreviewSize.x || sizes.get(i).getHeight() > maxPreviewSize.y) {
                    sizes.remove(i);
                    continue;
                }
            }
            if (minPreviewSize != null) {
                if (sizes.get(i).getWidth() < minPreviewSize.x || sizes.get(i).getHeight() < minPreviewSize.y) {
                    sizes.remove(i);
                }
            }
        }
//        if (sizes.size() == 0) {
//            String msg = "can not find suitable previewSize, now using default";
//            if (camera2Listener != null) {
//                Log.e(TAG, msg);
//                camera2Listener.onCameraError(new Exception(msg));
//            }
//            return defaultSize;
//        }
        Size bestSize = sizes.get(0);
        float previewViewRatio;
        if (previewViewSize != null) {
            previewViewRatio = (float) previewViewSize.x / (float) previewViewSize.y;
        } else {
            previewViewRatio = (float) bestSize.getWidth() / (float) bestSize.getHeight();
        }

        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio;
        }

        for (Size s : sizes) {
            if (specificPreviewSize != null && specificPreviewSize.x == s.getWidth() && specificPreviewSize.y == s.getHeight()) {
                return s;
            }
            if (Math.abs((s.getHeight() / (float) s.getWidth()) - previewViewRatio) < Math.abs(bestSize.getHeight() / (float) bestSize.getWidth() - previewViewRatio)) {
                bestSize = s;
            }
        }
        return bestSize;
    }

    private class OnImageAvailableListenerImpl implements ImageReader.OnImageAvailableListener {
        private byte[] y;
        private byte[] u;
        private byte[] v;
        private ReentrantLock lock = new ReentrantLock();

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            // Y:U:V == 4:2:2
//            if (camera2Listener != null && image.getFormat() == ImageFormat.YUV_420_888) {
//                Image.Plane[] planes = image.getPlanes();
//                // 加锁确保y、u、v来源于同一个Image
//                lock.lock();
//                // 重复使用同一批byte数组，减少gc频率
//                if (y == null) {
//                    y = new byte[planes[0].getBuffer().limit() - planes[0].getBuffer().position()];
//                    u = new byte[planes[1].getBuffer().limit() - planes[1].getBuffer().position()];
//                    v = new byte[planes[2].getBuffer().limit() - planes[2].getBuffer().position()];
//                }
//                if (image.getPlanes()[0].getBuffer().remaining() == y.length) {
//                    planes[0].getBuffer().get(y);
//                    planes[1].getBuffer().get(u);
//                    planes[2].getBuffer().get(v);
//                    camera2Listener.onPreview(y, u, v, mPreviewSize, planes[0].getRowStride());
//                }
//                lock.unlock();
//            }
//            image.close();
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
