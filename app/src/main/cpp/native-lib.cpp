#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

extern "C" {

using namespace cv;
using namespace std;

ANativeWindow *nativeWindow = 0;
CascadeClassifier *faceClassifier = 0;

void bitmap2Mat(JNIEnv *pEnv, jobject bitmap, Mat &mat) {
    AndroidBitmapInfo info;
    void *pixels = 0;
    //获取bitmap信息
    CV_Assert(AndroidBitmap_getInfo(pEnv, bitmap, &info) >= 0);
    //检查类型
    CV_Assert(info.format == ANDROID_BITMAP_FORMAT_RGBA_8888);
    //lock获取数据
    CV_Assert(AndroidBitmap_lockPixels(pEnv,bitmap,&pixels) >= 0);
    CV_Assert(pixels);

    mat.create(info.height, info.width, CV_8UC3);
    LOGI("bitmap2Mat: RGBA_8888 bitmap -> Mat");
    Mat tmp(info.height, info.width, CV_8UC4, pixels);
    cvtColor(tmp, mat, COLOR_RGBA2BGR);
    tmp.release();
    //unlock
    AndroidBitmap_unlockPixels(pEnv, bitmap);
}

JNIEXPORT jstring JNICALL
Java_com_fensite_facedetetion_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT void JNICALL
Java_com_fensite_facedetetion_MainActivity_loadModel(JNIEnv *env, jobject instance,
                                                  jstring absolutePath_) {
    const char *absolutePath = env->GetStringUTFChars(absolutePath_, 0);

    // TODO
    faceClassifier = new CascadeClassifier(absolutePath);

    env->ReleaseStringUTFChars(absolutePath_, absolutePath);
}

JNIEXPORT jboolean JNICALL
Java_com_fensite_facedetetion_MainActivity_process(JNIEnv *env, jobject instance,
        jobject bitmap) {
    Mat src;
    // 将bitmap转化成ndk能够识别的类型
    bitmap2Mat(env, bitmap, src);
    Mat grayMat;
    //灰度化
    cvtColor(src, grayMat, CV_BGR2GRAY);
    //灰度图直方均衡化
    equalizeHist(grayMat,grayMat);

    //图像识别
    vector<Rect> faces;
    faceClassifier->detectMultiScale(grayMat, faces);
    LOGI("faceClassifier->detectMultiScale");

    grayMat.release();

    //为所有的人脸画上矩形
    for (int i=0; i < faces.size(); ++i)
    {
        LOGI("faces.size");
        Rect face = faces[i];
        rectangle(src, face.tl(), face.br(), Scalar(0,255,255));
        LOGI("rectangle");
    }

    ANativeWindow_Buffer windowBuffer;
    //得到我的绘制空间
    if(ANativeWindow_lock(nativeWindow, &windowBuffer, 0)) {
        goto  end;
    }
    LOGI("ANativeWindow_lock");
    //直接在Native层进行数据填充，不传到java了
    cvtColor(src, src, CV_BGR2RGBA);
    //先对被填充图片大小进行归一
    resize(src, src, Size(windowBuffer.width, windowBuffer.height));
    memcpy(windowBuffer.bits, src.data, windowBuffer.height * windowBuffer.stride * 4);
    ANativeWindow_unlockAndPost(nativeWindow);

end:
    src.release();
    return true;
}

JNIEXPORT void JNICALL
Java_com_fensite_facedetetion_MainActivity_setSurface(JNIEnv *env, jobject instance,
                                                jobject surface,jint w, jint h) {
    if (surface && w && h)
    {
        if (nativeWindow)
        {
            LOGI("release old window");
            ANativeWindow_release(nativeWindow);
            nativeWindow = 0;
        }
        LOGI("new window");
        nativeWindow = ANativeWindow_fromSurface(env, surface);
        if (nativeWindow)
        {
            LOGI("push buffer to window");
            ANativeWindow_setBuffersGeometry(nativeWindow, w, h, WINDOW_FORMAT_RGBA_8888);
        }
    } else {
        if (nativeWindow)
        {
            LOGI("release old nativeWindow");
            ANativeWindow_release(nativeWindow);
            nativeWindow = 0;
        }
    }

}

JNIEXPORT void JNICALL
Java_com_fensite_facedetetion_MainActivity_destory(JNIEnv *env, jobject instance) {

    if (nativeWindow)
        ANativeWindow_release(nativeWindow);
    nativeWindow = 0;

    if (faceClassifier)
    {
        delete faceClassifier;
        faceClassifier = 0;
    }

}
}