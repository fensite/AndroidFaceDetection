package com.fensite.facetrace;

import android.view.Surface;

/**
 * create by zhaikn on 2019/7/19
 */
public class TraceHelper {
    static {
        System.loadLibrary("trace_native");
        System.loadLibrary("opencv_java3");
    }

    public static native void loadModel(String detectModel);
    public static native void startTracking();
    public static native void stopTracking();
    public static native void setSurface(Surface surface, int w, int h);
    public static native void detectorFace(byte[] data, int width, int height, int rotation, int mCameraId);
    public static native void destory();
}
