package com.example.tdycamera.api;

import static android.view.OrientationListener.ORIENTATION_UNKNOWN;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.SurfaceHolder;

import androidx.collection.SparseArrayCompat;


import com.example.tdycamera.base.AspectRatio;
import com.example.tdycamera.base.CameraViewImpl;
import com.example.tdycamera.base.Constants;
import com.example.tdycamera.base.PreviewImpl;
import com.example.tdycamera.base.Size;
import com.example.tdycamera.base.SizeMap;
import com.example.tdycamera.utils.MyLogUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;


@SuppressWarnings("deprecation")
public class Camera1 extends CameraViewImpl {
    private String TAG = "Camera1";

    private static final int INVALID_CAMERA_ID = -1;

    //闪光灯
    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    //闪光灯模式
    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int mCameraId;

    //AtomicBoolean在高并发的情况下只有一个线程能够访问这个属性值。
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);

    Camera mCamera;

    //相机参数
    private Camera.Parameters mCameraParameters;

    //相机信息
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    //预览尺寸
    private final SizeMap mPreviewSizes = new SizeMap();

    //照片尺寸
    private final SizeMap mPictureSizes = new SizeMap();

    //宽高比
    private AspectRatio mAspectRatio;

    //是否可以预览
    private boolean mShowingPreview;

    //是否自动对焦
    private boolean mAutoFocus;

    //是否是前置摄像头
    private int mFacing;

    //闪光灯模式
    private int mFlash;

    //显示方向
    private int mDisplayOrientation;

    public Camera1(Callback callback, PreviewImpl preview) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (mCamera != null) {
                    setUpPreview();
                    adjustCameraParameters();
                }
            }
        });
    }

    @Override
    public boolean start() {
        chooseCamera();
        openCamera();
        if (mPreview.isReady()) {
            setUpPreview();
        }
        mShowingPreview = true;
        mCamera.startPreview();
        return true;
    }

    @Override
    public void stop() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        releaseCamera();
    }

    // 控制相机 设置预览控件
    @SuppressLint("NewApi")
    void setUpPreview() {
        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
            } else {
                mCamera.setPreviewTexture((SurfaceTexture) mPreview.getSurfaceTexture());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isCameraOpened() {
        return mCamera != null;
    }

    //切换摄像头
    @Override
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

    @Override
    public int getFacing() {
        return mFacing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        SizeMap idealAspectRatios = mPreviewSizes;
        for (AspectRatio aspectRatio : idealAspectRatios.ratios()) {
            if (mPictureSizes.sizes(aspectRatio) == null) {
                idealAspectRatios.remove(aspectRatio);
            }
        }
        return idealAspectRatios.ratios();
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (mAspectRatio == null || !isCameraOpened()) {
            // Handle this later when camera is opened
            mAspectRatio = ratio;
            return true;
        } else if (!mAspectRatio.equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                mAspectRatio = ratio;
                adjustCameraParameters();
                return true;
            }
        }
        return false;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        if (setAutoFocusInternal(autoFocus)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    public boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public void setFlash(int flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            mCamera.setParameters(mCameraParameters);
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {
            throw new IllegalStateException(
                    "Camera is not ready. Call start() before takePicture().");
        }
        if (getAutoFocus()) {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
    }

    void takePictureInternal() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    isPictureCaptureInProgress.set(false);
                    mCallback.onPictureTaken(data);
                    camera.cancelAutoFocus();
                    camera.startPreview();
                }
            });
        }
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
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

    /**
     * 修改相机id
     */
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

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        mCamera = Camera.open(mCameraId);
        mCameraParameters = mCamera.getParameters();
        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        // AspectRatio
        if (mAspectRatio == null) {
            mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
        }
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
        mCallback.onCameraOpened();
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    //调整相机参数
    void adjustCameraParameters() {
        //SortedSet有序集合
        SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
        if (sizes == null) { // Not supported
            mAspectRatio = chooseAspectRatio();
            sizes = mPreviewSizes.sizes(mAspectRatio);
        }
        Size size = chooseOptimalSize(sizes);

        // 始终重新应用摄影机参数
        // 此比率中的最大图片大小
        final Size pictureSize = mPictureSizes.sizes(mAspectRatio).last();
        //在预览，先停止预览
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        //设置预览尺寸
        mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        //设置照片尺寸
        mCameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        //设置显示方向
        mCameraParameters.setRotation(calcCameraRotation(mDisplayOrientation));
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);
        mCamera.setParameters(mCameraParameters);
        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes) {
        if (!mPreview.isReady()) { // 尚未布置
            return sizes.first(); // 返回最小的大小
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (isLandscape(mDisplayOrientation)) {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        } else {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }
        Size result = null;
        for (Size size : sizes) { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;

            }
            result = size;
        }
        return result;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    /**
     * 计算显示方向
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
     * 测试提供的方向是否为横向。
     * 角度 (0,90,180,270)
     * 横向为True，纵向为false
     */
    private boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == Constants.LANDSCAPE_90 ||
                orientationDegrees == Constants.LANDSCAPE_270);
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

    /**
     * 设置闪光灯
     */
    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            //获得相机支持的闪光灯模式
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        } else {
            mFlash = flash;
            return false;
        }
    }

    public void getFlatten() {
        Camera.Parameters parameters = mCamera.getParameters();
        String str = parameters.flatten();
        MyLogUtil.e(TAG, "相机参数 " + str);
    }

    // 相机当前设置
    public Camera.Parameters getParameters() {
        Camera.Parameters parameters = mCamera.getParameters();
        boolean isExposureLock = parameters.getAutoExposureLock();
        MyLogUtil.e(TAG, "相机参数 自动曝光锁定的状态" + isExposureLock);
        boolean isAutoWhiteBalanceLock = parameters.getAutoWhiteBalanceLock();
        MyLogUtil.e(TAG, "相机参数 自动白平衡锁定的状态" + isAutoWhiteBalanceLock);
        String getColorEffect = parameters.getColorEffect();
        MyLogUtil.e(TAG, "相机参数 当前的色彩效果" + getColorEffect);
        int getExposureCompensation = parameters.getExposureCompensation();
        MyLogUtil.e(TAG, "相机参数 当前的曝光补偿指数" + getExposureCompensation);
        float getExposureCompensationStep = parameters.getExposureCompensationStep();
        MyLogUtil.e(TAG, "相机参数 曝光补偿步骤" + getExposureCompensationStep);
        String getFlashMode = parameters.getFlashMode();
        MyLogUtil.e(TAG, "相机参数 当前的闪光模式" + getFlashMode);
        float getFocalLength = parameters.getFocalLength();
        MyLogUtil.e(TAG, "相机参数 焦距" + getFocalLength);
        String getFocusMode = parameters.getFocusMode();
        MyLogUtil.e(TAG, "相机参数 当前对焦模式" + getFocusMode);
        int getMaxExposureCompensation = parameters.getMaxExposureCompensation();
        MyLogUtil.e(TAG, "相机参数 最大曝光补偿指数" + getMaxExposureCompensation);
        int getMinExposureCompensation = parameters.getMinExposureCompensation();
        MyLogUtil.e(TAG, "相机参数 最小曝光补偿指数" + getMinExposureCompensation);
        int getPreviewFormat = parameters.getPreviewFormat();
        MyLogUtil.e(TAG, "相机参数 预览格式" + getPreviewFormat);
        int getPreviewFrameRate = parameters.getPreviewFrameRate();
        MyLogUtil.e(TAG, "相机参数 帧速率设置（每秒帧数）" + getPreviewFrameRate);
        List<int[]> getSupportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
        for (int i = 0; i < getSupportedPreviewFpsRange.size(); i++) {
            int[] fpsRange = getSupportedPreviewFpsRange.get(i);
            String fpsRangeStr = "";
            for (int j = 0; j < fpsRange.length; j++) {
                fpsRangeStr = fpsRangeStr + fpsRange[j] + " ,";
            }
            MyLogUtil.e(TAG, "相机参数 支持的预览fps范围的列表 " + fpsRangeStr);
        }
        List<Integer> getSupportedPreviewFrameRates = parameters.getSupportedPreviewFrameRates();
        for (int i = 0; i < getSupportedPreviewFrameRates.size(); i++) {
            MyLogUtil.e(TAG, "相机参数 支持的预览帧速率列表 " + getSupportedPreviewFrameRates.get(i));
        }
        List<String> getSupportedWhiteBalance = parameters.getSupportedWhiteBalance();
        for (int i = 0; i < getSupportedWhiteBalance.size(); i++) {
            MyLogUtil.e(TAG, "相机参数 支持的白平衡列表 " + getSupportedWhiteBalance.get(i));
        }
        String getWhiteBalance = parameters.getWhiteBalance();
        MyLogUtil.e(TAG, "相机参数 当前的白平衡 " + getWhiteBalance);
        boolean isAutoExposureLockSupported = parameters.isAutoExposureLockSupported();
        MyLogUtil.e(TAG, "相机参数 是否支持自动白平衡锁定 " + isAutoExposureLockSupported);

//                E: 相机参数 自动曝光锁定的状态false
//                E: 相机参数 自动白平衡锁定的状态false
//                E: 相机参数 当前的色彩效果none 无
//                E: 相机参数 当前的曝光补偿指数0
//                E: 相机参数 曝光补偿步骤0.166667
//                E: 相机参数 当前的闪光模式null
//                E: 相机参数 焦距2.99
//                E: 相机参数 当前对焦模式 fixed 焦点是固定的
//                E: 相机参数 最大曝光补偿指数24
//                E: 相机参数 最小曝光补偿指数-24
//                E: 相机参数 预览格式17 NV21
//                E: 相机参数 帧速率设置（每秒帧数）30
//                E: 相机参数 支持的预览fps范围的列表 15000 ,15000 ,15000 ,
//                E: 相机参数 支持的预览fps范围的列表 20000 ,20000 ,20000 ,
//                E: 相机参数 支持的预览fps范围的列表 7000 ,7000 ,24000 ,
//                E: 相机参数 支持的预览fps范围的列表 24000 ,24000 ,24000 ,
//                E: 相机参数 支持的预览fps范围的列表 7000 ,7000 ,30000 ,
//                E: 相机参数 支持的预览fps范围的列表 30000 ,30000 ,30000 ,
//                E: 相机参数 支持的预览帧速率列表 15
//                E: 相机参数 支持的预览帧速率列表 20
//                E: 相机参数 支持的预览帧速率列表 24
//                E: 相机参数 支持的预览帧速率列表 30
//                E: 相机参数 支持的白平衡列表 auto 自动
//                E: 相机参数 支持的白平衡列表 incandescent 白炽灯
//                E: 相机参数 支持的白平衡列表 fluorescent 荧光
//                E: 相机参数 支持的白平衡列表 warm-fluorescent 暖荧光
//                E: 相机参数 支持的白平衡列表 daylight 日光
//                E: 相机参数 支持的白平衡列表 cloudy-daylight 阴天
//                E: 相机参数 支持的白平衡列表 twilight 暮光之城
//                E: 相机参数 支持的白平衡列表 shade 阴影
//                E: 相机参数 当前的白平衡 auto
//                E: 相机参数 是否支持自动白平衡锁定 true
        return mCamera.getParameters();
    }

    //传入参数名称设置
    public String setParameter(String key, String value, int num) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (key.equals("auto-exposure-lock")) {
                if (!getParameterSupport("auto-exposure-lock-supported")) {
                    return "不支持设置自动曝光锁定";
                }
            }
            if (key.equals("auto-whitebalance-lock")) {
                if (!getParameterSupport("auto-whitebalance-lock-supported")) {
                    return "不支持设置自动白平衡锁定";
                }
            }
            if (!TextUtils.isEmpty(value)) {
                parameters.set(key, value);
            } else if (num != -100) {
                parameters.set(key, num);
            }
            try {
                mCamera.setParameters(parameters);
                return "设置成功！";
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
        return "设置失败！";
    }

    //传入参数名称查看是否支持
    public boolean getParameterSupport(String key) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.get(key).equals("true");
        }
        return false;
    }

    public int getParameterValue(String key) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return Integer.parseInt(parameters.get(key));
        }
        return 0;
    }


    // 设置自动曝光锁定状态 不要在Camera.open()与Camera.startPreview()之间调用此方法
    public void setAutoExposureLock(boolean toggle) {
        if (!isAutoExposureLockSupported()) {
            return;
        }
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setAutoExposureLock(toggle);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //是否支持自动曝光锁定
    public boolean isAutoExposureLockSupported() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.isAutoExposureLockSupported();
        }
        return false;
    }

    //设置自动白平衡锁定状态 不要在Camera.open()与Camera.startPreview()之间调用此方法
    public void setAutoWhiteBalanceLock(boolean toggle) {
        if (!isAutoWhiteBalanceLockSupported()) {
            return;
        }
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setAutoWhiteBalanceLock(toggle);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //是否支持自动白平衡锁定
    public boolean isAutoWhiteBalanceLockSupported() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.isAutoWhiteBalanceLockSupported();
        }
        return false;
    }

    //获取当前的色彩效果设置
    public String getColorEffect() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getColorEffect();
        }
        return null;
    }

    //设置当前的色彩效果设置
    public void setColorEffect(String value) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setColorEffect(value);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //最小曝光补偿指数
    public synchronized int getMinExposureCompensation() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            //最小曝光补偿指数
            int minExpoDuration = parameters.getMinExposureCompensation();
            MyLogUtil.e(TAG, "相机参数 最小曝光补偿指数" + minExpoDuration);
            return minExpoDuration;
        }
        return 0;
    }

    //最大曝光补偿指数
    public synchronized int getMaxExposureCompensation() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            //最小曝光补偿指数
            int maxExpoDuration = parameters.getMaxExposureCompensation();
            MyLogUtil.e(TAG, "相机参数 最大曝光补偿指数" + maxExpoDuration);
            return maxExpoDuration;
        }
        return 0;
    }

    //当前曝光补偿指数
    public synchronized int getExposureCompensation() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            //当前曝光补偿指数
            int expoDuration = parameters.getExposureCompensation();
            MyLogUtil.e(TAG, "相机参数 当前曝光补偿指数" + expoDuration);
            return expoDuration;
        }
        return 0;
    }
    //将值设置在最大最小范围内
    public static double clampValue(double max, double min, double value) {
        return Math.min(Math.max(min, value), max);
    }
    //设置相机曝光补偿指数 有效值范围是getMinExposureCompensation()（包括）到getMaxExposureCompensation()（包括）//0表示不调整曝光。
    public synchronized int setExposureCompensation(int exposureCompensation) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            //最大曝光补偿指数
            int maxExpoDuration = parameters.getMaxExposureCompensation();
            //最小曝光补偿指数
            int minExpoDuration = parameters.getMinExposureCompensation();
            MyLogUtil.e(TAG, "相机参数 最大曝光补偿指数" + maxExpoDuration);
            MyLogUtil.e(TAG, "相机参数 最小曝光补偿指数" + minExpoDuration);
            double exposureDuration = clampValue(maxExpoDuration, minExpoDuration, exposureCompensation);
            MyLogUtil.e(TAG, "相机参数 设置曝光补偿指数" + minExpoDuration);
            parameters.setExposureCompensation((int) exposureDuration);
            try {
                mCamera.setParameters(parameters);
                return (int) exposureDuration;
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
        return 0;
    }

    //获取当前的闪光模式设置
    public String getFlashMode() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            //当前曝光补偿指数
            return parameters.getFlashMode();
        }
        return null;
    }

    //设置闪光模式
    public synchronized void setFlashMode(String flash) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            switch (flash) {
                case Camera.Parameters.FLASH_MODE_OFF: {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    break;
                }
                case Camera.Parameters.FLASH_MODE_ON: {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    break;
                }
                case Camera.Parameters.FLASH_MODE_AUTO: {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    break;
                }
                case Camera.Parameters.FLASH_MODE_TORCH: {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    break;
                }
                default:
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    break;
            }
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //获取支持的最大焦点区域数
    public synchronized int getMaxNumFocusAreas() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getMaxNumFocusAreas();
        }
        return 0;
    }

    //获取当前的重点领域
    public synchronized List<Camera.Area> getFocusAreas() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getFocusAreas();
        }
        return null;
    }

    //设置重点领域 如果getMaxNumFocusAreas值为0，则不支持聚焦区域。
    public synchronized void setFocusAreas(List<Camera.Area> focusAreas) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusAreas(focusAreas);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置对焦模式
    public synchronized void setFocusMode(String focus) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            switch (focus) {
                case Camera.Parameters.FOCUS_MODE_AUTO: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    break;
                }
                case Camera.Parameters.FOCUS_MODE_INFINITY: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                    break;
                }
                case Camera.Parameters.FOCUS_MODE_MACRO: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
                    break;
                }
                case Camera.Parameters.FOCUS_MODE_FIXED: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                    break;
                }
                case Camera.Parameters.FOCUS_MODE_EDOF: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_EDOF);
                    break;
                }
                case Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO: {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    break;
                }
                default:
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    break;
            }
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //对焦模式
    public synchronized List<String> getSupportedFocusModes() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> getSupportedFocusModes = parameters.getSupportedFocusModes();
            for (int i = 0; i < getSupportedFocusModes.size(); i++) {
                MyLogUtil.e(TAG, "相机参数 支持的对焦模式列表 " + getSupportedFocusModes.get(i));
            }
            return getSupportedFocusModes;
        }
        return null;
    }

    //调用一次对焦一次
    public void setAutoFocusLock(boolean isLock){
        if (mCamera != null){
            Camera.Parameters parameters = mCamera.getParameters();
            if(isLock){
                if(parameters.getFocusMode().equals(Camera.Parameters.FLASH_MODE_AUTO)){
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {

                        }
                    });
                }
            }else {
                mCamera.cancelAutoFocus();
                mCamera.autoFocus(null);
            }

        }
    }

    //设置GPS高度
    public void setGpsAltitude(double altitude) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setGpsAltitude(altitude);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置GPS纬度坐标
    public void setGpsLatitude(double latitude) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setGpsLatitude(latitude);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置GPS经度坐标
    public void setGpsLongitude(double longitude) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setGpsLongitude(longitude);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置GPS处理方法
    public void setGpsProcessingMethod(String processing_method) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setGpsProcessingMethod(processing_method);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置GPS时间戳
    public void setGpsTimestamp(long timestamp) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setGpsTimestamp(timestamp);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置拍摄图片的Jpeg质量 范围是1到100，最好是100。
    public void setJpegQuality(int quality) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setJpegQuality(quality);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置Jpeg图片中EXIF缩略图的质量 范围是1到100，最好是100。
    public void setJpegThumbnailQuality(int quality) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setJpegThumbnailQuality(quality);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置Jpeg图片中EXIF缩略图的尺寸 如果应用程序将宽度和高度都设置为0，则EXIF将不包含缩略图。
    public void setJpegThumbnailSize(int width, int height) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setJpegThumbnailSize(width, height);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //获取支持的最大测光区域数
    public synchronized int getMaxNumMeteringAreas() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getMaxNumMeteringAreas();
        }
        return 0;
    }

    //获取当前的测光区域
    public List<Camera.Area> getMeteringAreas() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getMeteringAreas();
        }
        return null;
    }

    //设置测光区域 如果getMaxNumMeteringAreas值为0，则不支持测光区域。
    public synchronized void setMeteringAreas(List<Camera.Area> meteringAreas) {
        if (getMaxNumMeteringAreas() == 0) {
            return;
        }
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setMeteringAreas(meteringAreas);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置图片的图像格式
    public void setPictureFormat(int pixel_format) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPictureFormat(pixel_format);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置图片的尺寸 参考setPreviewSize(int, int)
    public void setPictureSize(int width, int height) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPictureSize(width, height);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置预览图片的图像格式 默认格式为 ImageFormat.NV21
    public void setPreviewFormat(int pixel_format) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewFormat(pixel_format);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置最小和最大预览fps
    public synchronized void setPreviewFpsRange(int min, int max) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewFpsRange(min, max);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //获取支持的预览fps（每秒帧数）范围
    public synchronized List<String> getSupportedPreviewFpsRange() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            List<int[]> getSupportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();
            List<String> supportedPreviewFpsRange = new ArrayList<>();
            for (int i = 0; i < getSupportedPreviewFpsRange.size(); i++) {
                String range = "";
                for (int j = 0; j < getSupportedPreviewFpsRange.get(i).length; j++) {
                    range = range + getSupportedPreviewFpsRange.get(i)[j] + " ";
                }
                supportedPreviewFpsRange.add(range);
                MyLogUtil.e(TAG, "相机参数 支持的帧率范围列表 " + range);
            }
            return supportedPreviewFpsRange;
        }
        return null;
    }

    //设置接收预览帧的速率。这是目标帧率。实际帧速率取决于驱动程序。
    public synchronized void setPreviewFrameRate(int value) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewFrameRate(value);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //支持的帧率列表
    public synchronized List<Integer> getSupportedPreviewFrameRates() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            List<Integer> getSupportedPreviewFrameRates = parameters.getSupportedPreviewFrameRates();
            for (int i = 0; i < getSupportedPreviewFrameRates.size(); i++) {
                MyLogUtil.e(TAG, "相机参数 支持的帧率列表 " + getSupportedPreviewFrameRates.get(i));
            }
            return getSupportedPreviewFrameRates;
        }
        return null;
    }

    //设置预览图片的尺寸
    public void setPreviewSize(int width, int height) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(width, height);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置录制模式
    public synchronized void setRecordingHint(boolean hint) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setRecordingHint(hint);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //设置相对于相机方向的顺时针旋转角度（以度为单位）
    public void setRotation(int rotation) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setRotation(rotation);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    public void onOrientationChanged(int orientation) {
        if (orientation == ORIENTATION_UNKNOWN) return;
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(mCameraId, info);
        orientation = (orientation + 45) / 90 * 90;
        int rotation = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (info.orientation + orientation) % 360;
        }
        setRotation(rotation);
    }

    //设置场景模式
    public synchronized void setSceneMode(String value) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setSceneMode(value);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //支持场景模式列表
    public synchronized List<String> getSupportedSceneModes() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> getSupportedSceneMode = parameters.getSupportedSceneModes();
            for (int i = 0; i < getSupportedSceneMode.size(); i++) {
                MyLogUtil.e(TAG, "相机参数 支持的场景模式列表 " + getSupportedSceneMode.get(i));
            }
            return getSupportedSceneMode;
        }
        return null;
    }

    //启用和禁用视频稳定
    public void setVideoStabilization(boolean toggle) {
        if (!isVideoStabilizationSupported()) {
            return;
        }
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setVideoStabilization(toggle);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }

    }

    //是否支持视频稳定
    public boolean isVideoStabilizationSupported() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.isVideoStabilizationSupported();
        }
        return false;
    }

    //设置白平衡
    public synchronized void setWhiteBalance(String value) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setWhiteBalance(value);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //支持的白平衡列表
    public synchronized List<String> getSupportedWhiteBalance() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> getSupportedWhiteBalance = parameters.getSupportedWhiteBalance();
            for (int i = 0; i < getSupportedWhiteBalance.size(); i++) {
                MyLogUtil.e(TAG, "相机参数 支持的白平衡列表 " + getSupportedWhiteBalance.get(i));
            }
            return getSupportedWhiteBalance;
        }
        return null;
    }

    //设置当前缩放值
    public void setZoom(int value) {
        if (!isZoomSupported()) {
            return;
        }
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setZoom(value);
            try {
                mCamera.setParameters(parameters);
            } catch (Exception e) {
                // ignore failures for minor parameters like this for now
            }
        }
    }

    //是否支持缩放
    public boolean isZoomSupported() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.isZoomSupported();
        }
        return false;
    }

}
