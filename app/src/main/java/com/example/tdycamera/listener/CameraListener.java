package com.example.tdycamera.listener;


public interface CameraListener {
    //相机启动
    void onCameraOpened(int width, int height);
    //相机关闭
    void onCameraClosed();
    //相机数据
    void onCameraPreview(byte[] data, int width, int height , int displayOrientation);

    default void onPictureTaken(byte[] data){}
}
