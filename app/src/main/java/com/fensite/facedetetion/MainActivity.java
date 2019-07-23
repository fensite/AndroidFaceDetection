package com.fensite.facedetetion;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity implements SurfaceHolder.Callback {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java4");
    }

    // 加载咱们的经验文本
    private native void loadModel(String absolutePath);
    //将surface区域传到底层，底层绘制的区域由xml决定的，所以必须下传
    private native void setSurface(Surface surface, int w, int h);
    private native boolean process(Bitmap bitmap);
    private native void destory();

    private Bitmap mBitmap;
    private ProgressDialog progressDialog;
    private ProgressBar mProgressBar;
    private static String TAG = "MainActivity";

    @SuppressLint("StaticFieldLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        SurfaceView surfaceView = (SurfaceView)findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        // 加载训练样本线程，也就是经验
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    File dir = new File(Environment.getExternalStorageDirectory(), "face");
                    copyAssetsFile("haarcascade_frontalface_alt.xml", dir);
                    File file1 = new File(dir, "haarcascade_frontalface_alt.xml");
                    loadModel(file1.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPreExecute() {
                showDialog();
            }

            @Override
            protected void onPostExecute(Void result) {
                dismissLoading();
            }

        }.execute();

        if (Build.VERSION.SDK_INT >= 21) {
            //第二个参数是需要申请的权限
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
            {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        7);
            }
        } else {
            //权限已经被授予或当前版本无需动态申请权限，在这里直接写要执行的相应方法即可
        }
        // Example of a call to a native method
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    private void showDialog() {
        if(null == progressDialog) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setIndeterminate(true);
        }
        progressDialog.show();
    }

    private void dismissLoading() {
        if (null != progressDialog) {
            progressDialog.dismiss();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        setSurface(surfaceHolder.getSurface(), 640, 480);
        safeProcess();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    public void fromAlbum(View view)
    {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else {
            intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        intent.setType("image/*");

        //使用选取器并自定义标题
        startActivityForResult(Intent.createChooser(intent,"选区待识别的图片"),100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 100 && null != data)
        {
            getResult(data.getData());
        }
    }

    private void getResult(Uri uri) {
        safeRecycled();
        String imagePath = null;
        if (null != uri) {
            if ("file".equals(uri.getScheme())) {
                Log.i(TAG,"path uri 获得图片");
                imagePath = uri.getPath();
            } else if ("content".equals(uri.getScheme())) {
                Log.i(TAG,"content uri 获得图片");
                String[] filePathColumns = {MediaStore.Images.Media.DATA};
                Cursor cursor =getContentResolver().query(uri,filePathColumns,null,null,null);
                if (null != cursor) {
                    if (cursor.moveToFirst()) {
                        int columIndex = cursor.getColumnIndex(filePathColumns[0]);
                        imagePath = cursor.getString(columIndex);
                    }
                    cursor.close();
                }

            }
        }

        if (!TextUtils.isEmpty(imagePath)) {
            mBitmap = toBitmap(imagePath);
            safeProcess();
        }
    }

    public void safeProcess() {
        if (null != mBitmap && !mBitmap.isRecycled())
        {
            process(mBitmap);
        }
    }

    private Bitmap toBitmap(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        int width_tmp = options.outWidth, hight_tmp = options.outHeight;
        int scale = 1;

        while (true) {
            if (width_tmp <= 640 && hight_tmp <= 480) {
                break;
            }
            width_tmp /= 2;
            hight_tmp /= 2;
            scale *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = scale;
        o.outWidth = width_tmp;
        o.outHeight = hight_tmp;
        return BitmapFactory.decodeFile(filePath, o);
    }

    public void safeRecycled() {
        if (null != mBitmap && !mBitmap.isRecycled()) {
            mBitmap.recycle();
        }
        mBitmap = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destory();
        safeRecycled();
    }

    private void copyAssetsFile(String name, File dir) throws IOException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, name);
        if (!file.exists()) {
            InputStream is = getAssets().open(name);
            FileOutputStream fos = new FileOutputStream(file);
            int len;
            byte[] buffer = new byte[2048];
            while ((len = is.read(buffer)) != -1)
                fos.write(buffer, 0, len);
            fos.close();
            is.close();
        }
    }
}
