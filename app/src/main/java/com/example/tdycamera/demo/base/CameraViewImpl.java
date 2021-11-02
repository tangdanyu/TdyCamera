package com.example.tdycamera.demo.base;

import android.view.View;

import java.util.Set;

public abstract class CameraViewImpl {

    protected final Callback mCallback;

    protected final PreviewImpl mPreview;

    public CameraViewImpl(Callback callback, PreviewImpl preview) {
        mCallback = callback;
        mPreview = preview;
    }

    public View getView() {
        return mPreview.getView();
    }

    /**
     * 如果实现能够启动相机返回true
     */
    public abstract boolean start();

    public abstract void stop();

    public abstract boolean isCameraOpened();

    public abstract void setFacing(int facing);

    public abstract int getFacing();

    public abstract Set<AspectRatio> getSupportedAspectRatios();

    //设置宽高比
    public abstract boolean setAspectRatio(AspectRatio ratio);

    //获得宽高比
    public abstract AspectRatio getAspectRatio();

    //设置自动对焦
    public abstract void setAutoFocus(boolean autoFocus);

    //是否自动对焦
    public abstract boolean getAutoFocus();

    //设置闪光灯模式
    public abstract void setFlash(int flash);

    //获得闪光灯模式
    public abstract int getFlash();

    //拍照
    public abstract void takePicture();

    //设置显示方向
    public abstract void setDisplayOrientation(int displayOrientation);

    public interface Callback {

        //相机启动
        void onCameraOpened();
        //相机关闭
        void onCameraClosed();
        //拍照
        void onPictureTaken(byte[] data);

    }

}
