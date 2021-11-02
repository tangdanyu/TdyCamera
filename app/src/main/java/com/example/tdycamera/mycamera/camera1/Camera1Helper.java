package com.example.tdycamera.mycamera.camera1;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import androidx.collection.SparseArrayCompat;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;
import com.example.tdycamera.view.AutoFitTextureView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * camera1相机辅助类，和{@link CameraListener}共同使用，获取nv21数据等操作
 */
public class Camera1Helper implements Camera.PreviewCallback,
        Camera.PictureCallback,
        Camera.FaceDetectionListener,
        Camera.ErrorCallback, Camera.OnZoomChangeListener {

    private String TAG = "Camera1Helper";
    private static Camera1Helper instance = null;
    private Context mContext;                       //上下文
    private Camera mCamera;                         //相机实例
    private int mCameraId;                          //相机id
    private static final int INVALID_CAMERA_ID = -1;
    private int mFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;  //是否是前置摄像头
    private boolean mPreviewing;                    //是否正在预览
    private View mPreviewDisplayView;               // 预览显示的view，目前仅支持surfaceView和textureView
    private boolean isMirror = false;               // 是否镜像显示，只支持textureView
    private int mDisplayOrientation;                //显示方向
    private int mCameraOrientation;                 //相机方向
    private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();    //相机信息
    private Camera.Parameters mCameraParameters;    //相机参数
    private Camera.Size mPreviewSize;               //预览尺寸 默认是横屏width>height
    private Camera.Size mPictureSize;               //照片尺寸
    private Camera.Size mVideoSize;                        //录制视频尺寸 尺寸不要大于1080p，因为MediaRecorder无法处理如此高分辨率的视频。
    private int screenWidth, screenHeight;          //屏幕宽高 竖屏状态下width<height
    private CameraListener cameraListener;          //相机事件回调
    private int imageFormat = ImageFormat.NV21;     //视频帧格式
    private MediaRecorder mMediaRecorder;           //录制视频
    private boolean isRecording = false;            //是否正在录制
    private String mNextVideoAbsolutePath;          //录制视频路径
    private int mFlash;                             //闪光灯模式
    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    //闪光灯模式
    static {
        FLASH_MODES.put(0, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(1, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(2, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(3, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(4, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    //配置
    private boolean isAddCallbackBuffer = false;     //是否使用缓冲区
    private boolean isRecordVideo = true;          //是否录制视频
    private boolean isAutoFocus = false;            //是否自动对焦
    private boolean isShutterSound = false;         //是否禁用快门声音
    private boolean isFaceDetect = false;           //是否开启人脸检测
    private boolean isZoom = false;                 //是否缩放

    //AtomicBoolean在高并发的情况下只有一个线程能够访问这个属性值。
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);

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
    private SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
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
        //打开相机获得相机实例
        openCamera();
        //设置相机参数
        setParameters();
        //设置预览回调
        setPreviewCallback();
        //设置预览显示方向
        setDisplayOrientation();
        //开始预览
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
            mCamera.release();
            mCamera = null;
            mPreviewing = false;
        }
    }

    //设置预览控件
    private void setPreviewCallback() {
        MyLogUtil.e(TAG, "setPreview");
        try {
            if (mPreviewDisplayView != null) {
                if (mPreviewDisplayView instanceof TextureView) {
                    mCamera.setPreviewTexture(((TextureView) mPreviewDisplayView).getSurfaceTexture());
                } else {
                    mCamera.setPreviewDisplay(((SurfaceView) mPreviewDisplayView).getHolder());
                }
                if (isAddCallbackBuffer) {
                    //设置缓冲区
                    if (imageFormat == ImageFormat.NV21) {
                        mCamera.addCallbackBuffer(new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2]);
                        mCamera.addCallbackBuffer(new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2]);
                        mCamera.addCallbackBuffer(new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2]);
                        MyLogUtil.e(TAG, "NV21=" + mPreviewSize.width * mPreviewSize.height * 3 / 2);//2764800
                    } else if (imageFormat == ImageFormat.YV12) {
                        mCamera.addCallbackBuffer(new byte[getYV12Size(mPreviewSize.width, mPreviewSize.height)]);
                        mCamera.addCallbackBuffer(new byte[getYV12Size(mPreviewSize.width, mPreviewSize.height)]);
                        mCamera.addCallbackBuffer(new byte[getYV12Size(mPreviewSize.width, mPreviewSize.height)]);
                        MyLogUtil.e(TAG, "YV12 size=" + getYV12Size(mPreviewSize.width, mPreviewSize.height)
                                + " width=" + mPreviewSize.width + " height=" + mPreviewSize.height);//size =2764800 width=1920 height=960
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

    //计算YV12数据大小
    private int getYV12Size(int width, int height) {
        int yStride = (int) Math.ceil(width / 16.0) * 16;
        int uvStride = (int) Math.ceil((yStride / 2) / 16.0) * 16;
        int ySize = yStride * height;
        int uvSize = uvStride * height / 2;
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

        //设置方向
        setRotation();

        //设置预览尺寸
        setPreviewSize();

        //设置照片尺寸
        setPictureSize();

        //设置视频尺寸
        setVideoSize();

        //设置自动对焦
        setAutoFocus(isAutoFocus);

        setFlash(mFlash);

        //设置禁用快门声音
        setShutterSound(isShutterSound);

        //设置录制视频参数
        setMediaRecorderConfig();

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
            mPreviewSize = closelySize;
            if (mPreviewDisplayView instanceof TextureView) {
                ((AutoFitTextureView) mPreviewDisplayView).setAspectRatio(mPreviewSize.height, mPreviewSize.width);
            }
            mCameraParameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

            if (cameraListener != null) {
                cameraListener.onCameraOpened(mPreviewSize.width, mPreviewSize.height, mCameraOrientation);//如果画出来的框在左下方正，这里的角度可能不对
            }
        }
    }

    //设置视频尺寸
    private void setVideoSize() {
        MyLogUtil.e(TAG, "setVideoSize");
        if (mCamera == null) {
            return;
        }
        mVideoSize = mCameraParameters.getPreferredPreviewSizeForVideo();
//        if(mVideoSize.width == 1920 && mVideoSize.height ==1080){
//            return;
//        }
        List<Camera.Size> sizeList = mCameraParameters.getSupportedVideoSizes();
        MyLogUtil.e(TAG, "支持的视频尺寸=" + mCameraParameters.get("video-size-values"));
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
            if(mVideoSize.width>=1920 || mVideoSize.height >= 1080){
                mVideoSize.width = 1920;
                mVideoSize.height = 1080;
            }
            MyLogUtil.e(TAG, "视频尺寸修改为：" + closelySize.width + "*" + closelySize.height);
            mVideoSize = closelySize;
        }
    }

    //设置图片尺寸,高质量图片
    private void setPictureSize() {
        MyLogUtil.e(TAG, "setPictureSize");
        mPictureSize = mCameraParameters.getPictureSize();
        List<Camera.Size> sizeList = mCameraParameters.getSupportedPictureSizes();
        MyLogUtil.e(TAG, "支持的图片尺寸=" + mCameraParameters.get("picture-size-values"));
        int w = 0, h = 0;
        for (Camera.Size size : sizeList) {
            if (size.width > w || size.height > h) {
                w = size.width;
                h = size.height;
            }
        }
        mPictureSize.width = w;
        mPictureSize.height = h;
        MyLogUtil.e(TAG, "图片尺寸修改为：" + mPictureSize.width + "*" + mPictureSize.height);
        mCameraParameters.setPictureSize(mPictureSize.width, mPictureSize.height);
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

    //设置闪光灯
    private void setFlash(int flash) {
        if (isCameraOpened()) {
            //获得相机支持的闪光灯模式
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = 0;
            }
        } else {
            mFlash = flash;
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

    //设置预览显示的顺时针旋转,设置预览成像的方向
    private void setDisplayOrientation() {
        MyLogUtil.e(TAG, "setDisplayOrientation");
        if (mCamera != null) {
            //获取屏幕旋转的方向
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            mDisplayOrientation = getDisplayOrientation(rotation);
            MyLogUtil.e(TAG, "屏幕方向=" + rotation);
            MyLogUtil.e(TAG, "预览显示方向=" + mDisplayOrientation);
            MyLogUtil.e(TAG, "相机方向=" + mCameraInfo.orientation);
            mCamera.setDisplayOrientation(mDisplayOrientation);
        }
    }

    //设置照片的方向
    private void setRotation() {
        MyLogUtil.e(TAG, "setRotation");
        if (mCamera != null) {
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            mCameraOrientation = getRotation(rotation);
            MyLogUtil.e(TAG, "屏幕方向=" + rotation);//竖屏下为0
            MyLogUtil.e(TAG, "照片方向=" + mCameraOrientation);
            MyLogUtil.e(TAG, "相机方向=" + mCameraInfo.orientation);//前置为270，后置为90
            mCameraParameters.setRotation(mCameraOrientation);
        }
    }

    //设置是否人脸检测
    private void setFaceDetection(boolean isFaceDetect) {
        if (isFaceDetect && mCamera != null) {
            mCamera.setFaceDetectionListener(this);
        }
    }

    //停止人脸检测
    private void stopFaceDetection() {
        if (isFaceDetect && mCamera != null) {
            mCamera.stopFaceDetection();
        }
    }

    //开始平滑缩放
    private void startSmoothZoom(int value) {
        if (mCamera != null) {
            if (mCameraParameters.isSmoothZoomSupported() && isZoom) {
                mCamera.startSmoothZoom(value);
            }
        }
    }

    //停止缩放
    private void stopSmoothZoom() {
        if (mCamera != null) {
            if (mCameraParameters.isSmoothZoomSupported() && isZoom) {
                mCamera.stopSmoothZoom();
            }
        }
    }

    //设置缩放监听
    private void setZoom() {
        if (mCamera != null) {
            if (mCameraParameters.isSmoothZoomSupported() && isZoom) {
                mCamera.setZoomChangeListener(this);
            }
        }
    }

    //计算预览方向
    private int getDisplayOrientation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//前置
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // 后置
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    //计算相机的旋转角度 用于输出JPEG
    private int getRotation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//前置
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {  // 后置
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    //提供的方向是否为横向。角度 (0,90,180,270)横向为True，纵向为false
    private boolean isLandscape(int screenOrientationDegrees) {
        return (screenOrientationDegrees == 90 ||
                screenOrientationDegrees == 270);
    }

    // 初始化预览控件
    private void initSurface() {
        // 如果SurfaceTexture已经就绪, 则直接打开相机, 否则等待SurfaceTexture已经就绪的回调
        if (mPreviewDisplayView instanceof TextureView) {
            if (((TextureView) this.mPreviewDisplayView).isAvailable()) {
                start();
            } else {
                ((TextureView) this.mPreviewDisplayView).setSurfaceTextureListener(mSurfaceTextureListener);
            }
            if (isMirror) {
                mPreviewDisplayView.setScaleX(-1);
            }
        } else if (mPreviewDisplayView instanceof SurfaceView) {
            ((SurfaceView) mPreviewDisplayView).getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            ((SurfaceView) mPreviewDisplayView).getHolder().addCallback(mSurfaceHolderCallback);
        }
    }

    //获得相机实例
    public Camera getCamera() {
        return mCamera;
    }

    //单例模式
    public static Camera1Helper getInstance(Context context, View previewDisplayView, CameraListener cameraListener) {
        if (instance == null) {
            instance = new Camera1Helper(context, previewDisplayView, cameraListener);
        }
        return instance;
    }

    // 参数设置
    public Camera1Helper(Context context, View previewDisplayView, CameraListener cameraListener) {
        this.mContext = context;
        this.cameraListener = cameraListener;
        this.mPreviewDisplayView = previewDisplayView;
        if (isRecordVideo) {
            initMediaRecorder();
        }
        // 获取当前的屏幕尺寸, 放到一个点对象里
        Point screenSize = new Point();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);
        MyLogUtil.e(TAG, "屏幕宽高 (x,y) x=" + screenSize.x + " y=" + screenSize.y); // x=1080 y=2160
    }

    //切换摄像头，要先停止再开启
    public void switchCamera() {
        MyLogUtil.e(TAG, "切换摄像头");
        mFacing = (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) ?
                Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    //页面可见，设置预览回调
    public void onResume() {
        MyLogUtil.e(TAG, "onResume");
        initSurface();
    }

    //页面不可见时，停止摄像头
    public void onPause() {
        MyLogUtil.e(TAG, "onPause");
        stop();
    }

    //销毁相机
    public void onDestroy() {
        MyLogUtil.e(TAG, "onDestroy");
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        cameraListener = null;
    }

    //是否是前置摄像头
    public boolean isFrontCamera() {
        return Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraId;
    }

    //预览数据回调
    @Override
    public void onPreviewFrame(byte[] nv21, Camera camera) {
        if (isAddCallbackBuffer) {
            if (imageFormat == ImageFormat.NV21) {
                mCamera.addCallbackBuffer(new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2]);
            } else if (imageFormat == ImageFormat.YV12) {
                mCamera.addCallbackBuffer(new byte[getYV12Size(mPreviewSize.width, mPreviewSize.height)]);
            }
        }
        if (cameraListener != null) {
            cameraListener.onCameraPreview(nv21, mPreviewSize.width, mPreviewSize.height, mCameraOrientation);
        }
    }

    //拍照回调
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        MyLogUtil.e(TAG, "照片数据");
        camera.cancelAutoFocus();
        mCamera.startPreview();
        if (isFaceDetect && mCameraParameters.getMaxNumDetectedFaces() > 0) {
            MyLogUtil.e(TAG, "支持最多人脸数=" + mCameraParameters.getMaxNumDetectedFaces());
            mCamera.startFaceDetection();
        }
        isPictureCaptureInProgress.set(false);
        if (cameraListener != null) {
            cameraListener.onPictureTaken(bytes);
        }
    }

    @Override
    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
        MyLogUtil.e(TAG, "人脸检测=" + faces.length);
    }

    @Override
    public void onError(int i, Camera camera) {
        MyLogUtil.e(TAG, "相机错误=" + i);
    }

    @Override
    public void onZoomChange(int i, boolean b, Camera camera) {
        MyLogUtil.e(TAG, "缩放");
    }

    //拍照
    public void takePicture() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            if (mCamera != null) {
                MyLogUtil.e(TAG, "拍照了");
                mCamera.takePicture(null, null, null, this);
            }
        }
    }

    //初始化MediaRecorder
    private void initMediaRecorder() {
        MyLogUtil.e(TAG, "initMediaRecorder");
        mMediaRecorder = new MediaRecorder();
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    //设置录制视频参数
    public void setMediaRecorderConfig() {
        MyLogUtil.e(TAG, "setMediaRecorderConfig");
        mMediaRecorder.setCamera(mCamera);//camera1
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//camera
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(mVideoSize.width, mVideoSize.height);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(8 * mVideoSize.width * mVideoSize.height);
        Surface surface = new Surface(((TextureView) mPreviewDisplayView).getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(mContext);
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setOrientationHint(mCameraOrientation);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //是否正在录制视频
    public boolean isRecording() {
        return isRecording;
    }
    public boolean isRecordVideo(){
        return isRecordVideo;
    }

    //开始录制
    public void startRecord() {
        MyLogUtil.e(TAG, "startRecord");
        if (isRecordVideo) {
            isRecording = true;
            //Unlock and set camera to MediaRecorder
            //取消锁定，让录制视频工具可用
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

            MyLogUtil.e(TAG, "视频录制 " + mNextVideoAbsolutePath);///storage/emulated/0/Android/data/com.example.tdycamera/files/1635497535274.mp4
            mNextVideoAbsolutePath = null;
        }

    }


}
