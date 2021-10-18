package com.example.tdycamera.mycamera.camera1;

import static android.view.OrientationListener.ORIENTATION_UNKNOWN;

import android.content.Context;
import android.hardware.Camera;

import com.example.tdycamera.utils.MyLogUtil;

import java.util.ArrayList;
import java.util.List;

public class Camera1SettingsHelper {
    private String TAG = "Camera1SettingsHelper";
    private Camera mCamera;
    private Camera1Helper camera1Helper;

    public Camera1SettingsHelper(Context context) {
        camera1Helper =  Camera1Helper.getInstance(context, null, null);
        camera1Helper.onResume();
        this.mCamera = camera1Helper.getCamera();
    }

    //将值设置在最大最小范围内
    public static double clampValue(double max, double min, double value) {
        return Math.min(Math.max(min, value), max);
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

    //获得支持的色彩效果设置
    public List<String> getSupportedColorEffects() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getSupportedColorEffects();
        }
        return null;
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

    //获得支持的闪光模式
    public List<String> getSupportedFlashModes() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getSupportedFlashModes();
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
                case Camera.Parameters.FLASH_MODE_RED_EYE:{
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_RED_EYE);
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
    //当前对焦模式
    public synchronized String getFocusMode() {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            return parameters.getFocusMode();
        }
        return null;
    }
    //调用一次对焦一次
    public void setAutoFocusLock(boolean isLock) {
        if (mCamera != null) {
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
