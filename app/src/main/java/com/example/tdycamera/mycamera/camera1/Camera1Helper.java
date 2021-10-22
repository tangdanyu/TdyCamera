package com.example.tdycamera.mycamera.camera1;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import com.example.tdycamera.apicamera.CameraView;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.view.AutoFitTextureView;
import com.example.tdycamera.utils.MyLogUtil;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.LogRecord;


/**
 * 相机辅助类，和{@link CameraListener}共同使用，获取nv21数据等操作
 */
public class Camera1Helper implements Camera.PreviewCallback,
        Camera.PictureCallback,
        Camera.FaceDetectionListener,
        Camera.ErrorCallback, Camera.OnZoomChangeListener {

    private String TAG = "Camera1Helper";
    private Context mContext;   //上下文
    private Camera mCamera;
    private static Camera1Helper instance = null;
    private int mCameraId;    //相机id
    private static final int INVALID_CAMERA_ID = -1;
    private int mFacing = CameraView.FACING_FRONT;//是否是前置摄像头
    private boolean mPreviewing;    //是否正在预览
    private View previewDisplayView;    // 预览显示的view，目前仅支持surfaceView和textureView
    private boolean isMirror = false;    // 是否镜像显示，只支持textureView
    private int mDisplayOrientation;    //显示方向
    private int mCameraOrientation;    //相机方向
    private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();    //相机信息
    private Camera.Parameters mCameraParameters;  //相机参数
    private Camera.Size previewSize;//预览宽高
    private int screenWidth, screenHeight;  // 屏幕宽高
    private CameraListener cameraListener;  //相机事件回调
    private int imageFormat = ImageFormat.NV21; //视频帧格式
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;//是否正在录制视频

    //配置
    private boolean isAddCallbackBuffer = true;//是否使用缓冲区
    private boolean isRecordVideo = false;//是否录制视频
    private boolean isAutoFocus = false;    //是否自动对焦
    private boolean isShutterSound = false; //是否禁用快门声音
    private boolean isFaceDetect = false; //是否开启人脸检测
    private boolean isZoom = false; //是否缩放

    //SurfaceTexture与相机关联
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);// width=1080, height=2160
            screenWidth = width;
            screenHeight = height;
            start();    // SurfaceTexture就绪后回调执行打开相机操作
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
                start();    // SurfaceTexture就绪后回调执行打开相机操作
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

        //选择特定的相机
        chooseCamera();
        //没有相机
        if (mCameraId == -1) {
            return;
        }
        openCamera();
        setParameters();
        setPreview();
        mCamera.startPreview();
        if (isFaceDetect && mCameraParameters.getMaxNumDetectedFaces() > 0) {
            MyLogUtil.e(TAG, "支持最多人脸数=" + mCameraParameters.getMaxNumDetectedFaces());
            mCamera.startFaceDetection();
        }
        mPreviewing = true;
    }

    // 停止预览
    private synchronized void stop() {
        MyLogUtil.e(TAG, "stop");
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mPreviewing = false;
        }
    }

    //设置预览控件
    private void setPreview() {
        MyLogUtil.e(TAG, "setPreview");
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
                        MyLogUtil.e(TAG,"NV21="+previewSize.width * previewSize.height * 3 / 2);//2764800
                    } else if (imageFormat == ImageFormat.YV12) {
                        mCamera.addCallbackBuffer(new byte[getYV12Size(previewSize.width,previewSize.height)]);
                        mCamera.addCallbackBuffer(new byte[getYV12Size(previewSize.width,previewSize.height)]);
                        mCamera.addCallbackBuffer(new byte[getYV12Size(previewSize.width,previewSize.height)]);
                        MyLogUtil.e(TAG,"YV12 size="+getYV12Size(previewSize.width,previewSize.height)
                                +" width="+previewSize.width+" height="+previewSize.height);//size =2764800 width=1920 height=960
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

    private int getYV12Size(int width,int height){
        int yStride   = (int) Math.ceil(width / 16.0) * 16;
        int uvStride  = (int) Math.ceil((yStride / 2) / 16.0) * 16;
        int ySize     = yStride * height;
        int uvSize    = uvStride * height / 2;
        int size = ySize + uvSize * 2;
        return size;
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
                MyLogUtil.e(TAG, "chooseCamera=" + mCameraId);
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    //打开相机
    private void openCamera() {
        MyLogUtil.e(TAG, "openCamera");
        if (mCamera != null) {
            return;
        }
        mCamera = Camera.open(mCameraId);
        mCamera.setErrorCallback(this);
    }

    //调整相机参数
    private void setParameters() {
        MyLogUtil.e(TAG, "setParameters");
        mCameraParameters = mCamera.getParameters();

        //在预览，先停止预览
        if (mPreviewing) {
            mCamera.stopPreview();
        }
        //视频帧格式设置
        mCameraParameters.setPreviewFormat(imageFormat);

        //设置录制模式提示
        mCameraParameters.setRecordingHint(true);//去掉这句，12fps

        //设置显示方向
        setDisplayOrientation();

        //设置预览尺寸
        setPreviewSize();

        //设置自动对焦
        setAutoFocus(isAutoFocus);

        //设置禁用快门声音
        setShutterSound(isShutterSound);


        //设置录制视频参数
        setMediaRecorderConfig(isRecordVideo, previewSize.width, previewSize.height, mCameraOrientation);

        //设置人脸检测
        setFaceDetection(isFaceDetect);

        //设置缩放
        setZoom();

        mCamera.setParameters(mCameraParameters);
//        MyLogUtil.e(TAG, "相机参数="+mCameraParameters.flatten() );
    }

    //设置预览尺寸
    private void setPreviewSize() {
        MyLogUtil.e(TAG, "setPreviewSize");
        if (mCamera == null) {
            return;
        }
        List<Camera.Size> sizeList = mCameraParameters.getSupportedPreviewSizes();
        MyLogUtil.e(TAG, "支持的预览尺寸=" + mCameraParameters.get("preview-size-values"));
        Camera.Size closelySize = null;//储存最合适的尺寸
        for (Camera.Size size : sizeList) { //先查找preview中是否存在与surfaceview相同宽高的尺寸
            if ((size.width == screenHeight) && (size.height == screenWidth)) {
                closelySize = size;
            }
        }
        if (closelySize == null) {
            // 得到与传入的宽高比最接近的size
            float reqRatio = ((float) screenHeight) / screenWidth;
            MyLogUtil.e(TAG, "宽高比 " + reqRatio);
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
                cameraListener.onCameraOpened(previewSize.width, previewSize.height, mCameraOrientation);//如果画出来的框在左下方正，这里的角度可能不对
            }
        }
    }

    //设置/取消自动对焦
    public void setAutoFocus(boolean isLock) {
        MyLogUtil.e(TAG, "setAutoFocus");
        if (mCamera != null && mPreviewing) {
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
    private void setShutterSound(boolean enabled) {
        MyLogUtil.e(TAG, "setShutterSound");
        if (mCamera != null) {
            Camera.getCameraInfo(mCameraId, mCameraInfo);
            if (mCameraInfo.canDisableShutterSound) {
                mCamera.enableShutterSound(enabled);
            }
        }
    }

    private void setDisplayOrientation() {
        MyLogUtil.e(TAG, "setDisplayOrientation");
        if (mCamera != null) {
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            mDisplayOrientation = calcDisplayOrientation(rotation);
            mCameraOrientation = calcCameraRotation(rotation);
            MyLogUtil.e(TAG, "显示方向=" + mDisplayOrientation);
            MyLogUtil.e(TAG, "相机方向=" + mCameraOrientation);
//            mCameraParameters.setRotation(mCameraOrientation);
            mCamera.setDisplayOrientation(calcDisplayOrientation(rotation));
        }
    }

    //设置是否人脸检测
    private void setFaceDetection(boolean isFaceDetect) {
        if (isFaceDetect && mCamera != null) {
            mCamera.setFaceDetectionListener(this);
        }
    }

    //停止人脸检测
    private void stopFaceDetection(){
        if (isFaceDetect && mCamera != null) {
            mCamera.stopFaceDetection();
        }
    }
    //开始平滑缩放
    private void startSmoothZoom(int value){
        if(mCamera!= null){
            if(mCameraParameters.isSmoothZoomSupported() && isZoom){
                mCamera.startSmoothZoom(value);
            }
        }
    }
    //停止缩放
    private void stopSmoothZoom(){
        if(mCamera!= null){
            if(mCameraParameters.isSmoothZoomSupported() && isZoom){
                mCamera.stopSmoothZoom();
            }
        }
    }
    private void setZoom(){
        if(mCamera!= null){
            if(mCameraParameters.isSmoothZoomSupported() && isZoom){
                mCamera.setZoomChangeListener(this);
            }
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
        if (isRecordVideo) {
            initMediaRecorder();
        }
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
        MyLogUtil.e(TAG, "onPause");
        stop();
    }

    //销毁相机
    public void onDestroy() {
        MyLogUtil.e(TAG, "onDestroy");
        mCamera.release();
        mCamera = null;
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
                mCamera.addCallbackBuffer(new byte[getYV12Size(previewSize.width,previewSize.height)]);
            }
        }
        if (cameraListener != null) {
            cameraListener.onCameraPreview(nv21, previewSize.width, previewSize.height, mCameraOrientation);
        }
    }

    //拍照
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        if (cameraListener != null) {
            cameraListener.onPictureTaken(bytes);
        }
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        MyLogUtil.e(TAG,"人脸检测="+faces.length);
    }

    @Override
    public void onError(int i, Camera camera) {
        MyLogUtil.e(TAG,"相机错误="+i);
    }


    @Override
    public void onZoomChange(int i, boolean b, Camera camera) {
        MyLogUtil.e(TAG,"缩放");
    }

    public void takePicture() {
        if (mCamera != null) {
            MyLogUtil.e(TAG, "拍照了");
            mCamera.takePicture(null, null, null, this);
            mCamera.stopPreview();
            mCamera.startPreview();
            if (isFaceDetect && mCameraParameters.getMaxNumDetectedFaces() > 0) {
                MyLogUtil.e(TAG, "支持最多人脸数=" + mCameraParameters.getMaxNumDetectedFaces());
                mCamera.startFaceDetection();
            }
        }
    }

    //初始化MediaRecorder
    private void initMediaRecorder() {
        MyLogUtil.e(TAG, "initMediaRecorder");
        mMediaRecorder = new MediaRecorder();
    }

    //设置录制视频参数
    public void setMediaRecorderConfig(boolean isRecordVideo, int width, int height, int mCameraOrientation) {
        if (isRecordVideo) {
            MyLogUtil.e(TAG, "setMediaRecorderConfig");
            File file = new File(mContext.getExternalCacheDir(), "demo.mp4");
            if (file.exists()) {
                file.delete();
            }
            MyLogUtil.e(TAG, "视频录制" + file.getPath());
            mMediaRecorder.setCamera(mCamera);//camera1
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//camera1
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//camera2
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoSize(width, height);//注意要颠倒
            mMediaRecorder.setOrientationHint(mCameraOrientation);
            mMediaRecorder.setVideoFrameRate(30);
            mMediaRecorder.setVideoEncodingBitRate(8 * width * height);
            Surface surface = new Surface(((TextureView) previewDisplayView).getSurfaceTexture());
            mMediaRecorder.setPreviewDisplay(surface);
            mMediaRecorder.setOutputFile(file.getAbsolutePath());
            try {
                mMediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //是否正在录像
    public boolean isRecording() {
        return isRecording;
    }

    //开始录制
    public void startRecord() {
        MyLogUtil.e(TAG, "startRecord");
        if (isRecordVideo) {
            isRecording = true;
            //Unlock and set camera to MediaRecorder
            mCamera.unlock();
            if (mMediaRecorder != null) {
                //camera1不支持录制的同时调用onPreviewFrame。
                mMediaRecorder.start();
            }
        }
    }

    //结束录制
    public void stopRecord() {
        MyLogUtil.e(TAG, "stopRecord");
        if (isRecordVideo) {
            isRecording = false;
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
//        try {
//            mCamera.reconnect();
//            MyLogUtil.e(TAG,"重新设置回调");
//            mCamera.setPreviewCallback(Camera1Helper.this);
//            mCamera.startPreview();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


}
