package com.example.tdycamera.listener;


import android.graphics.Bitmap;
import android.media.Image;
import android.util.Size;

public interface CameraListener {
    //相机启动
    void onCameraOpened(int width, int height, int displayOrientation);

    //相机关闭
    default  void onCameraClosed(){}

    //相机数据
    void onCameraPreview(byte[] data, int width, int height, int displayOrientation);

    default void onPreview(byte[] y, byte[] u, byte[] v, Size previewSize, int stride) {
    }
    default void onPreviewFrame(Image image, int width, int height, int orientation){}

    default void onPictureTaken(byte[] data) {
    }

    default void onBitmap(Bitmap bitmap, int displayOrientation) {
    }
}
