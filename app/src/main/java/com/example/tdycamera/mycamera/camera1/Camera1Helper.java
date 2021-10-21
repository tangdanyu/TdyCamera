package com.example.tdycamera.mycamera.camera1;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import com.example.tdycamera.apicamera.CameraView;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.view.AutoFitTextureView;
import com.example.tdycamera.utils.MyLogUtil;

import java.io.IOException;
import java.util.List;


/**
 * 相机辅助类，和{@link CameraListener}共同使用，获取nv21数据等操作
 */
public class Camera1Helper implements Camera.PreviewCallback, Camera.PictureCallback {

    private String TAG = "CameraHelper";
    private Context mContext;   //上下文
    private Camera mCamera;
    private static Camera1Helper instance = null;
    private int mCameraId;    //相机id
    private static final int INVALID_CAMERA_ID = -1;
    private int mFacing = CameraView.FACING_FRONT;//是否是前置摄像头
    private boolean mPreviewing;    //是否正在预览
    private boolean mAutoFocus;    //是否自动对焦
    private boolean mShutterSound; //是否禁用快门声音
    private View previewDisplayView;    // 预览显示的view，目前仅支持surfaceView和textureView
    private boolean isMirror = false;    // 是否镜像显示，只支持textureView
    private int mSensorOrientation = 0;    //相机方向
    private int mdisple = 0;    //相机方向
    private Camera.CameraInfo mCameraInfo;    //相机信息
    private Camera.Parameters mCameraParameters;  //相机参数
    private Camera.Size previewSize;//预览宽高
    private int screenWidth, screenHeight;  // 屏幕宽高
    private CameraListener cameraListener;  //相机事件回调
    private int imageFormat = ImageFormat.NV21; //视频帧格式
    private boolean isAddCallbackBuffer = false;//是否使用缓冲区
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;//是否正在录制视频
    private boolean isAutoFocus = false;//是否自动对焦

    //SurfaceTexture与相机关联
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);// width=1080, height=2160
            screenWidth = width;
            screenHeight = height;
            openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
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
                MyLogUtil.e(TAG, "surfaceChanged width" + width + " height" + height);
                screenWidth = width;
                screenHeight = height;
                openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stop();
        }
    };

    //开始预览
    private synchronized void start() {
        MyLogUtil.e(TAG, "start");
        if (mCamera != null) {
            return;
        }
        //选择特定的相机
        chooseCamera();
        //没有相机
        if (mCameraId == -1) {
            return;
        }
        openCamera();
        setPreview();
        mCamera.startPreview();
        mPreviewing = true;
    }

    // 停止预览
    private synchronized void stop() {
        MyLogUtil.e(TAG, "stop");
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mPreviewing = false;
        }
    }

    //设置预览控件
    private void setPreview() {
        try {
            if (previewDisplayView != null) {
                if (previewDisplayView instanceof TextureView) {
                    mCamera.setPreviewTexture(((TextureView) previewDisplayView).getSurfaceTexture());
                } else {
                    mCamera.setPreviewDisplay(((SurfaceView) previewDisplayView).getHolder());
                }
                if (isAddCallbackBuffer) {
                    if (imageFormat == ImageFormat.NV21) {
                        mCamera.addCallbackBuffer(new byte[previewSize.width * previewSize.height * 3 / 2]);
                        mCamera.addCallbackBuffer(new byte[previewSize.width * previewSize.height * 3 / 2]);
                        mCamera.addCallbackBuffer(new byte[previewSize.width * previewSize.height * 3 / 2]);
                    } else if (imageFormat == ImageFormat.YV12) {

                    }

                    mCamera.setPreviewCallbackWithBuffer(this);
                } else {
                    mCamera.setPreviewCallback(this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //相机是否已经打开
    private boolean isCameraOpened() {
        return mCamera != null;
    }


    //修改相机id
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
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
        mCamera.setDisplayOrientation(mSensorOrientation);
    }

    //调整相机参数
    private void adjustCameraParameters() {
        mCameraParameters = mCamera.getParameters();

        //在预览，先停止预览
        if (mPreviewing) {
            mCamera.stopPreview();
        }
        //视频帧格式设置
        mCameraParameters.setPreviewFormat(imageFormat);

        //设置显示方向
        mCameraParameters.setRotation(mSensorOrientation);

        //设置录制模式提示
        mCameraParameters.setRecordingHint(true);//去掉这句，12fps

        //设置预览尺寸
        setPreviewSize();

        //设置自动对焦
        setAutoFocus(mAutoFocus);

        //设置禁用快门声音
        setShutterSound(mShutterSound);

        //设置显示方向
        setDisplayOrientation();


        mCamera.setParameters(mCameraParameters);

    }

    //设置预览尺寸
    private void setPreviewSize() {
        if (mCamera == null) {
            return;
        }
        List<Camera.Size> sizeList = mCameraParameters.getSupportedPreviewSizes();

        Camera.Size closelySize = null;//储存最合适的尺寸
        for (Camera.Size size : sizeList) { //先查找preview中是否存在与surfaceview相同宽高的尺寸
//            MyLogUtil.e(TAG, "width" + size.width + " height" + size.height);
//            MyLogUtil.e(TAG, "surfaceWidth" + surfaceWidth + " surfaceHeight" + surfaceHeight);
            if ((size.width == screenWidth) && (size.height == screenHeight)) {
                closelySize = size;
            }
        }
        if (closelySize == null) {
            // 得到与传入的宽高比最接近的size
            float reqRatio = ((float) screenWidth) / screenHeight;
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
            MyLogUtil.e(TAG, "预览尺寸修改为：" + closelySize.width + "*" + closelySize.height);
            previewSize = closelySize;
            if (previewDisplayView instanceof TextureView) {
                ((AutoFitTextureView) previewDisplayView).setAspectRatio(previewSize.height, previewSize.width);
            }
            mCameraParameters.setPreviewSize(previewSize.width, previewSize.height);
            if (cameraListener != null) {
                cameraListener.onCameraOpened(previewSize.width, previewSize.height, mSensorOrientation);
            }
        }
    }

    //设置/取消自动对焦
    public void setAutoFocus(boolean isLock) {
        if (mCamera != null&& mPreviewing) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (isLock) {
                if (parameters.getFocusMode().equals(Camera.Parameters.FLASH_MODE_AUTO)) {
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {

                        }
                    });
                }
            } else {
                mCamera.cancelAutoFocus();
                mCamera.autoFocus(null);
            }

        }
    }

    //禁用快门声音
    private void setShutterSound(boolean enabled){
        if (mCamera!= null){
            if(mCameraInfo.canDisableShutterSound){
                mCamera.enableShutterSound(enabled);
            }
        }
    }

    private void setDisplayOrientation(  ){
        if(mCamera!= null){
            int rotation =  ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            mCamera.setDisplayOrientation(calcDisplayOrientation(rotation));
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

    // 初始化预览控件
    private void initSurface() {

        if (previewDisplayView instanceof TextureView) {
            if (((TextureView) this.previewDisplayView).isAvailable()) {
                start();
            } else {
                ((TextureView) this.previewDisplayView).setSurfaceTextureListener(mSurfaceTextureListener);
            }
            if (isMirror) {
                previewDisplayView.setScaleX(-1);
            }
        } else if (previewDisplayView instanceof SurfaceView) {
            ((SurfaceView) previewDisplayView).getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            ((SurfaceView) previewDisplayView).getHolder().addCallback(surfaceCallback);
        }
    }

    public Camera getCamera() {
        return mCamera;
    }

    public static Camera1Helper getInstance(Context context, CameraListener cameraListener, View previewDisplayView) {
        if (instance == null) {
            instance = new Camera1Helper(context, cameraListener, previewDisplayView);
        }

        return instance;
    }

    // 参数设置
    public Camera1Helper(Context context, CameraListener cameraListener, View previewDisplayView) {
        this.mContext = context;
        this.cameraListener = cameraListener;
        this.previewDisplayView = previewDisplayView;
        initMediaRecorder();
        // 获取当前的屏幕尺寸, 放到一个点对象里
        Point screenSize = new Point();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);
        MyLogUtil.e(TAG, "屏幕宽高 (x,y) x=" + screenSize.x + " y=" + screenSize.y); // x=1080 y=2160
    }

    //切换摄像头
    public void switchCamera() {
        mFacing = (mCameraId == CameraView.FACING_FRONT) ?
                CameraView.FACING_BACK : CameraView.FACING_FRONT;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    public void onResume() {
        MyLogUtil.e(TAG, "onResume");
        // 当屏幕关闭后重新打开, 若SurfaceTexture已经就绪, 此时onSurfaceTextureAvailable不会被回调, 这种情况下
        // 如果SurfaceTexture已经就绪, 则直接打开相机, 否则等待SurfaceTexture已经就绪的回调
        initSurface();

    }

    public void onPause() {
        MyLogUtil.e(TAG, "onPause: ");
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
        if (isAddCallbackBuffer) {
            if (imageFormat == ImageFormat.NV21) {
                mCamera.addCallbackBuffer(new byte[previewSize.width * previewSize.height * 3 / 2]);
            } else if (imageFormat == ImageFormat.YV12) {

            }
        }
        if (cameraListener != null) {
            cameraListener.onCameraPreview(nv21, previewSize.width, previewSize.height, mSensorOrientation);
        }
    }

    //拍照
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        stop();
        start();
        if (cameraListener != null) {
            cameraListener.onPictureTaken(bytes);
        }

    }

    public void takePicture() {
        if (mCamera != null) {
            MyLogUtil.e(TAG, "拍照了");
            mCamera.takePicture(null, null, null, this);
        }
    }

    //初始化MediaRecorder
    private void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    //是否正在录像
    public boolean isRecording() {
        return isRecording;
    }

    //开始录制
    public void startRecord() {
        MyLogUtil.e(TAG, "startRecord");
        isRecording = true;
        mMediaRecorder.start();
    }

    //结束录制
    public void stopRecord() {
        MyLogUtil.e(TAG, "startRecord");
        isRecording = false;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }
}
