package com.example.tdycamera.mycamera.camera1;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mycamera.camera2.view.AutoFitTextureView;
import com.example.tdycamera.utils.MyLogUtil;

import java.io.IOException;
import java.util.List;


/**
 * 相机辅助类，和{@link CameraListener}共同使用，获取nv21数据等操作
 */
public class Camera1Helper implements Camera.PreviewCallback,Camera.PictureCallback{
    private String TAG = "CameraHelper";
    private static final int INVALID_CAMERA_ID = -1;
    //相机id
    private int mCameraId;
    //是否是前置摄像头
    private int mFacing = 1;
    //是否正在预览
    private boolean mShowingPreview;
    //是否自动对焦
    private boolean mAutoFocus;
    // 预览显示的view，目前仅支持surfaceView和textureView
    private View previewDisplayView;
    // 是否镜像显示，只支持textureView
    private boolean isMirror = false;
    //预览宽高
    private int surfaceWidth, surfaceHeight;
    //显示方向
    private int mDisplayOrientation = 0;

    private Camera mCamera;
    //相机信息
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    //相机参数
    private Camera.Parameters mCameraParameters;
    private Camera.Size previewSize;


    //事件回调
    private CameraListener cameraListener;
    //上下文
    private Context context;
    // 预览控件TextureView与相机关联
    /**
     * SurfaceTexture监听器
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);//1080 2160
//            openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
            if (mCamera != null) {
                surfaceWidth = height;
                surfaceHeight = width;
                MyLogUtil.e(TAG, "surfaceChanged surfaceWidth" + surfaceWidth + " surfaceHeight" + surfaceHeight);
                setPreview();
                adjustCameraParameters();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    // 预览控件与相机关联
    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (mCamera != null) {
                surfaceWidth = height;
                surfaceHeight = width;
                MyLogUtil.e(TAG, "surfaceChanged surfaceWidth" + surfaceWidth + " surfaceHeight" + surfaceHeight);
                setPreview();
                adjustCameraParameters();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stop();
        }
    };


    // 参数设置
    public Camera1Helper(Context context, CameraListener cameraListener, View previewDisplayView) {
        this.context = context;
        this.cameraListener = cameraListener;
        this.previewDisplayView = previewDisplayView;
        init();

    }

    // 初始化相机
    public void init() {
        //预览 预览需要实现 android.view.SurfaceHolder.Callback 接口，该接口用于将图片数据从相机硬件传递到相机应用。
        if (previewDisplayView instanceof TextureView) {
            ((TextureView) this.previewDisplayView).setSurfaceTextureListener(mSurfaceTextureListener);
        } else if (previewDisplayView instanceof SurfaceView) {
            ((SurfaceView) previewDisplayView).getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            ((SurfaceView) previewDisplayView).getHolder().addCallback(surfaceCallback);
        }

        if (isMirror) {
            previewDisplayView.setScaleX(-1);
        }
    }

    //打开 Camera 对象 打开相机的操作可以推迟到 onResume() 方法，这样便于重用代码并尽可能简化控制流程。
    private void start() {
        MyLogUtil.e(TAG,"start");
        synchronized (this) {
            if (mCamera != null) {
                return;
            }
            chooseCamera();
            //没有相机
            if (mCameraId == -1) {
                return;
            }
            openCamera();
            setPreview();
            mShowingPreview = true;
            mCamera.startPreview();
        }
    }

    // 停止预览
    private void stop() {
        synchronized (this) {
            if (mCamera != null) {
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                mShowingPreview = false;
                mCamera.release();
                mCamera = null;
            }
        }
    }

    //设置预览控件
    private void setPreview() {
        try {
            if (previewDisplayView instanceof TextureView) {
                mCamera.setPreviewTexture(((TextureView) previewDisplayView).getSurfaceTexture());
            } else {
                mCamera.setPreviewDisplay(((SurfaceView) previewDisplayView).getHolder());
            }
            mCamera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //相机是否已经打开
    private boolean isCameraOpened() {
        return mCamera != null;
    }

    //切换摄像头
    public void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    //修改相机id
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
//            mCameraId = Camera.getNumberOfCameras() - 1;
            if (mCameraInfo.facing == mFacing) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    //打开相机
    private void openCamera() {
        if (mCamera != null) {
            stop();
        }
        mCamera = Camera.open(mCameraId);
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
    }

    //调整相机参数
    private void adjustCameraParameters() {
        mCameraParameters = mCamera.getParameters();

        //在预览，先停止预览
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        //视频帧格式设置
        mCameraParameters.setPreviewFormat(ImageFormat.NV21);

        previewSize = mCameraParameters.getPreviewSize();
        //设置预览尺寸
        changePreviewSize();

        //设置显示方向
        mCameraParameters.setRotation(calcCameraRotation(mDisplayOrientation));

        //设置聚焦模式
        setAutoFocusInternal(mAutoFocus);

        //设置录制模式提示
        mCameraParameters.setRecordingHint(true);//去掉这句，12fps
        mCamera.setParameters(mCameraParameters);

        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    public boolean isReady() {
        return previewDisplayView.getWidth() != 0 && previewDisplayView.getHeight() != 0;
    }

    //修改相机的预览尺寸
    private void changePreviewSize() {

        if (mCamera == null) {
            return;
        }
        List<Camera.Size> sizeList = mCameraParameters.getSupportedPreviewSizes();

        Camera.Size closelySize = null;//储存最合适的尺寸
        for (Camera.Size size : sizeList) { //先查找preview中是否存在与surfaceview相同宽高的尺寸
//            MyLogUtil.e(TAG, "width" + size.width + " height" + size.height);
//            MyLogUtil.e(TAG, "surfaceWidth" + surfaceWidth + " surfaceHeight" + surfaceHeight);
            if ((size.width == surfaceWidth) && (size.height == surfaceHeight)) {
                closelySize = size;
            }
        }
        if (closelySize == null) {
            // 得到与传入的宽高比最接近的size
            float reqRatio = ((float) surfaceWidth) / surfaceHeight;
//            MyLogUtil.e(TAG, "宽高比 " + reqRatio);
            float curRatio, deltaRatio;
            float deltaRatioMin = Float.MAX_VALUE;
            for (Camera.Size size : sizeList) {
                if (size.width < 240) continue;//1024表示可接受的最小尺寸，否则图像会很模糊，可以随意修改
                curRatio = ((float) size.width) / size.height;
                deltaRatio = Math.abs(reqRatio - curRatio);
                if (deltaRatio < deltaRatioMin) {
                    deltaRatioMin = deltaRatio;
                    closelySize = size;
                }
            }
        }
        if (closelySize != null) {
            surfaceHeight = closelySize.height;
            surfaceWidth = closelySize.width;
            MyLogUtil.e(TAG, "预览尺寸修改为：" + closelySize.width + "*" + closelySize.height);
            if (cameraListener != null) {
                cameraListener.onCameraOpened(closelySize.width, closelySize.height, calcCameraRotation(mDisplayOrientation));
            }
            previewSize = closelySize;
            if (previewDisplayView instanceof TextureView) {
                ((AutoFitTextureView) previewDisplayView).setAspectRatio(previewSize.height, previewSize.width);
            }
            mCameraParameters.setPreviewSize(closelySize.width, closelySize.height);
        }
    }

    /**
     * 计算显示方向 设置预览角度
     * 此计算用于确定预览的方向
     * Note: 这与摄影机旋转的计算不同
     */
    private int calcDisplayOrientation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    /**
     * 计算相机的旋转角度
     * 这个计算用于输出JPEG
     * Note: 这与显示方向的计算不同
     */
    private int calcCameraRotation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {  // back-facing
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    /**
     * 提供的方向是否为横向。
     * 角度 (0,90,180,270)
     * 横向为True，纵向为false
     */
    private boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == 90 ||
                orientationDegrees == 270);
    }

    /**
     * 设置是否自动对焦
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        mAutoFocus = autoFocus;
        if (isCameraOpened()) {
            //获得支持的聚焦模式
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {//持续对焦
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {//固定焦距
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {//无穷远
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                mCameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        } else {
            return false;
        }
    }

    private void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            mCameraParameters.setRotation(calcCameraRotation(displayOrientation));
            mCamera.setParameters(mCameraParameters);
            mCamera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
        }
    }

    // 开始预览
    public void onResume() {
        start();
    }

    // 停止预览
    public void onPause() {
        stop();
    }

    //销毁相机
    public void onDestroy() {
        stop();
        cameraListener = null;
    }

    // 是否是前置摄像头
    public boolean isFrontCamera() {
        return Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraId;
    }


    // 预览数据
    @Override
    public void onPreviewFrame(byte[] nv21, Camera camera) {
        if (cameraListener != null) {
            cameraListener.onCameraPreview(nv21, previewSize.width, previewSize.height, calcCameraRotation(mDisplayOrientation));
        }
    }


    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        if (cameraListener != null) {
            cameraListener.onPictureTaken(bytes);
        }
    }
}
