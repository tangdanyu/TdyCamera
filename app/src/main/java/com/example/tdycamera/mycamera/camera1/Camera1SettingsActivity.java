package com.example.tdycamera.mycamera.camera1;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import com.example.tdycamera.R;
import com.example.tdycamera.utils.MyLogUtil;

import java.util.ArrayList;
import java.util.List;

public class Camera1SettingsActivity extends AppCompatActivity {
    private static String TAG = "Camera1SettingsActivity";

    private static Camera1SettingsHelper camera1SettingsHelper;

    private static void doDataBind(ListPreference preference, List<String> entry, List<String> entryValues) {
        if (entry == null) {
            preference.setEnabled(false);
            preference.setSummary("不支持");
        } else {
            CharSequence mentries[] = new String[entry.size()];
            CharSequence mentryValues[] = new String[entry.size()];

            for(int i =0;i<entry.size();i++){
                mentries[i] = entry.get(i);
                mentryValues[i] = entryValues.get(i);
            }
            preference.setEntries(mentries);
            preference.setEntryValues(mentryValues);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        camera1SettingsHelper = new Camera1SettingsHelper(this);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            //是否支持自动曝光锁定
            Preference preferenceIsAutoExposureLockSupported = findPreference("isAutoExposureLockSupported");
            boolean isAutoExposureLockSupported = camera1SettingsHelper.isAutoExposureLockSupported();
            MyLogUtil.e(TAG, "是否支持自动曝光锁定=" + isAutoExposureLockSupported);
            preferenceIsAutoExposureLockSupported.setEnabled(isAutoExposureLockSupported);
            preferenceIsAutoExposureLockSupported.setSummary(isAutoExposureLockSupported ? "支持" : "不支持");

            if (isAutoExposureLockSupported) {
                //设置自动曝光锁定状态
                SwitchPreference switchAutoExposureLock = findPreference("setAutoExposureLock");
                switchAutoExposureLock.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MyLogUtil.e(TAG, "是否锁定" + newValue);
                        camera1SettingsHelper.setAutoExposureLock((boolean) newValue);
                        return true;//设置为true，按钮生效
                    }
                });
            }

            //是否支持自动白平衡锁定
            Preference preferenceAutoWhiteBalanceLock = findPreference("isAutoWhiteBalanceLockSupported");
            boolean isAutoWhiteBalanceLockSupported = camera1SettingsHelper.isAutoWhiteBalanceLockSupported();
            MyLogUtil.e(TAG, "是否支持自动白平衡锁定=" + isAutoWhiteBalanceLockSupported);
            preferenceAutoWhiteBalanceLock.setEnabled(isAutoWhiteBalanceLockSupported);
            preferenceAutoWhiteBalanceLock.setSummary(isAutoWhiteBalanceLockSupported ? "支持" : "不支持");

            if (isAutoWhiteBalanceLockSupported) {
                //设置自动白平衡锁定状态
                SwitchPreference switchAutoWhiteBalanceLock = findPreference("setAutoWhiteBalanceLock");
                switchAutoWhiteBalanceLock.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MyLogUtil.e(TAG, "是否锁定=" + newValue);
                        camera1SettingsHelper.setAutoWhiteBalanceLock((boolean) newValue);
                        return true;//设置为true，按钮生效
                    }
                });
            }


            //获得支持的色彩效果设置
            ListPreference setColorEffect = findPreference("setColorEffect");
            List<String> getSupportedColorEffects = camera1SettingsHelper.getSupportedColorEffects();
            String ColorEffect = camera1SettingsHelper.getColorEffect();
            MyLogUtil.e(TAG, "获取当前的色彩效果设置=" + ColorEffect);
            setColorEffect.setSummary(ColorEffect == null ? "无" : ColorEffect);
            doDataBind(setColorEffect, getSupportedColorEffects, getSupportedColorEffects);

            //设置相机曝光补偿指数
            ListPreference setExposureCompensation = findPreference("setExposureCompensation");
            //最大曝光补偿指数
            int getMaxExposureCompensation = camera1SettingsHelper.getMaxExposureCompensation();
            //最小曝光补偿指数
            int getMinExposureCompensation = camera1SettingsHelper.getMinExposureCompensation();
            MyLogUtil.e(TAG, "最大曝光补偿指数=" + getMaxExposureCompensation);
            MyLogUtil.e(TAG, "最小曝光补偿指数=" + getMinExposureCompensation);
            List<String> exposureList = new ArrayList<>();
            for (int i = getMinExposureCompensation; i <= getMaxExposureCompensation; i++) {
                exposureList.add(String.valueOf(i));
            }
            int getExposureCompensation = camera1SettingsHelper.getExposureCompensation();
            MyLogUtil.e(TAG, "当前曝光补偿指数=" + getExposureCompensation);
            setExposureCompensation.setSummary(String.valueOf(getExposureCompensation));
            doDataBind(setExposureCompensation, exposureList, exposureList);
            setExposureCompensation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setExposureCompensation.setSummary(String.valueOf(newValue));
                    return true;
                }
            });

            //设置闪光模式
            ListPreference setFlashMode = findPreference("setFlashMode");
            List<String> getSupportedFlashModes = camera1SettingsHelper.getSupportedFlashModes();
            String getFlashMode = camera1SettingsHelper.getFlashMode();
            MyLogUtil.e(TAG, "获取当前的闪光模式设置=" + getFlashMode);
            if (getFlashMode != null) {
                setFlashMode.setSummary(getFlashMode);
            }
            doDataBind(setFlashMode, getSupportedFlashModes, getSupportedFlashModes);

            setFlashMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setFlashMode.setSummary(String.valueOf(newValue));
                    return true;
                }
            });

            //设置对焦模式
            ListPreference setFocusMode = findPreference("setFocusMode");
            List<String> getSupportedFocusModes = camera1SettingsHelper.getSupportedFocusModes();
            String getFocusMode = camera1SettingsHelper.getFocusMode();
            MyLogUtil.e(TAG, "当前对焦模式=" + getFocusMode);
            setFocusMode.setSummary(getFocusMode);
            doDataBind(setFocusMode, getSupportedFocusModes, getSupportedFocusModes);

            setFocusMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setFocusMode.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setFlashMode(String.valueOf(newValue));
                    return true;
                }
            });

            //设置拍摄图片的Jpeg质量
            SeekBarPreference setJpegQuality = findPreference("setJpegQuality");
            setJpegQuality.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    camera1SettingsHelper.setJpegQuality((int) newValue);
                    MyLogUtil.e(TAG, "设置拍摄图片的Jpeg质量=" + newValue);
                    return true;
                }
            });

            //设置Jpeg图片中EXIF缩略图的质量
            SeekBarPreference setJpegThumbnailQuality = findPreference("setJpegThumbnailQuality");
            setJpegThumbnailQuality.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    camera1SettingsHelper.setJpegThumbnailQuality((int) newValue);
                    MyLogUtil.e(TAG, "设置Jpeg图片中EXIF缩略图的质量=" + newValue);
                    return true;
                }
            });
            //支持的Jpeg图片中EXIF缩略图的尺寸
            ListPreference setJpegThumbnailSize = findPreference("setJpegThumbnailSize");
            List<Camera.Size> getSupportedJpegThumbnailSizes = camera1SettingsHelper.getSupportedJpegThumbnailSizes();

            if (getSupportedJpegThumbnailSizes == null) {
                setJpegThumbnailSize.setEnabled(false);
                setJpegThumbnailSize.setSummary("不支持");
            } else {
                CharSequence mentries[] = new String[getSupportedJpegThumbnailSizes.size()];
                CharSequence mentryValues[] = new String[getSupportedJpegThumbnailSizes.size()];
                int i = 0;
                for (Camera.Size size : getSupportedJpegThumbnailSizes) {
                    mentries[i] = size.width + "*" + size.height;
                    mentryValues[i] = size.width + "*" + size.height;
                    i++;
                }
                setJpegThumbnailSize.setEntries(mentries);
                setJpegThumbnailSize.setEntryValues(mentryValues);
            }

            setJpegThumbnailSize.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String[] size = String.valueOf(newValue).split("\\*");
                    MyLogUtil.e(TAG, "设置Jpeg图片中EXIF缩略图的尺寸=" + size[0] + "*" + size[1]);
                    setJpegThumbnailSize.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setJpegThumbnailSize(Integer.valueOf(size[0]), Integer.valueOf(size[1]));
                    return true;
                }
            });
            //设置测光区域
            Preference setMeteringAreas = findPreference("setMeteringAreas");
            //获取支持的最大测光区域数
            int getMaxNumMeteringAreas = camera1SettingsHelper.getMaxNumMeteringAreas();
            if (getMaxNumMeteringAreas == 0) {
                setMeteringAreas.setEnabled(false);
                setMeteringAreas.setSummary("不支持");
                MyLogUtil.e(TAG, "不支持测光区域");
            } else {
                MyLogUtil.e(TAG, "获取支持的最大测光区域数=" + getMaxNumMeteringAreas);
            }
            //获取当前的测光区域
            List<Camera.Area> getMeteringAreas = camera1SettingsHelper.getMeteringAreas();
            if (getMeteringAreas != null) {
                for (int i = 0; i < getMeteringAreas.size(); i++) {
                    MyLogUtil.e(TAG, "获取当前的测光区域=" + getMeteringAreas.get(i).rect.toString());
                }
            }
            //设置图片的图像格式
            ListPreference setPictureFormat = findPreference("setPictureFormat");
            List<Integer> getSupportedPictureFormats = camera1SettingsHelper.getSupportedPictureFormats();
            List<String> getSupportedPictureFormatsEntry = new ArrayList<>();
            List<String> getSupportedPictureFormatsEntryValues = new ArrayList<>();
            if (getSupportedPictureFormats != null) {
                for (int i = 0; i < getSupportedPictureFormats.size(); i++) {
                    getSupportedPictureFormatsEntry.add(String.valueOf(getSupportedPictureFormats.get(i)));
                    if (getSupportedPictureFormats.get(i) ==ImageFormat.JPEG) {
                        getSupportedPictureFormatsEntryValues.add("JPEG");
                    } else {
                        getSupportedPictureFormatsEntryValues.add(String.valueOf(getSupportedPictureFormats.get(i)));
                    }
                    MyLogUtil.e(TAG, "支持的图片的图像格式=" + getSupportedPictureFormats.get(i));
                }
                doDataBind(setPictureFormat, getSupportedPictureFormatsEntryValues, getSupportedPictureFormatsEntryValues);
            } else {
                setPictureFormat.setEnabled(false);
                setPictureFormat.setSummary("不支持");
            }
            setPictureFormat.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setPictureFormat.setSummary((String) newValue);
                    return true;
                }
            });
            //设置图片的尺寸
            ListPreference setPictureSize = findPreference("setPictureSize");
            List<Camera.Size> getSupportedPictureSizes = camera1SettingsHelper.getSupportedPictureSizes();

            if (getSupportedPictureSizes == null) {
                setPictureSize.setEnabled(false);
                setPictureSize.setSummary("不支持");
            } else {
                CharSequence mentries[] = new String[getSupportedPictureSizes.size()];
                CharSequence mentryValues[] = new String[getSupportedPictureSizes.size()];
                int i = 0;
                for (Camera.Size size : getSupportedPictureSizes) {
                    mentries[i] = size.width + "*" + size.height;
                    mentryValues[i] = size.width + "*" + size.height;
                    i++;
                }
                setPictureSize.setEntries(mentries);
                setPictureSize.setEntryValues(mentryValues);
            }

            setPictureSize.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String[] size = String.valueOf(newValue).split("\\*");
                    MyLogUtil.e(TAG, "设置图片的尺寸=" + size[0] + "*" + size[1]);
                    setPictureSize.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setPictureSize(Integer.valueOf(size[0]), Integer.valueOf(size[1]));
                    return true;
                }
            });

            //设置预览的图像格式
            ListPreference setPreviewFormat = findPreference("setPreviewFormat");
            List<Integer> getSupportedPreviewFormats = camera1SettingsHelper.getSupportedPreviewFormats();
            List<String> getSupportedPreviewFormatsEntry = new ArrayList<>();
            List<String> getSupportedPreviewFormatsEntryValues = new ArrayList<>();
            if (getSupportedPreviewFormats != null) {
                for (int i = 0; i < getSupportedPreviewFormats.size(); i++) {
                    getSupportedPreviewFormatsEntry.add(String.valueOf(getSupportedPreviewFormats.get(i)));
                    if (getSupportedPreviewFormats.get(i) == ImageFormat.NV21) {
                        getSupportedPreviewFormatsEntryValues.add("NV21");
                    } else if (getSupportedPreviewFormats.get(i)==ImageFormat.YV12) {
                        getSupportedPreviewFormatsEntryValues.add("YV12");
                    } else {
                        getSupportedPreviewFormatsEntryValues.add(String.valueOf(getSupportedPreviewFormats.get(i)));
                    }
                    MyLogUtil.e(TAG, "支持的预览的图像格式=" + getSupportedPreviewFormatsEntryValues.get(i));
                }
                doDataBind(setPreviewFormat, getSupportedPreviewFormatsEntryValues,getSupportedPreviewFormatsEntry);
            } else {
                setPreviewFormat.setEnabled(false);
                setPreviewFormat.setSummary("不支持");
            }
            setPreviewFormat.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    MyLogUtil.e(TAG, "设置预览的图像格式=" + newValue);
                    int format = Integer.parseInt(String.valueOf(newValue));
                    camera1SettingsHelper.setPreviewFormat(format);
                    return true;
                }
            });


            //设置最小和最大预览fps
            ListPreference setPreviewFpsRange = findPreference("setPreviewFpsRange");
            List<String> getSupportedPreviewFpsRange = camera1SettingsHelper.getSupportedPreviewFpsRange();

            if (getSupportedPreviewFpsRange != null) {
                doDataBind(setPreviewFpsRange, getSupportedPreviewFpsRange,getSupportedPreviewFpsRange);

            } else {
                setPreviewFpsRange.setEnabled(false);
                setPreviewFpsRange.setSummary("不支持");
            }
            setPreviewFpsRange.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setPreviewFpsRange.setSummary(String.valueOf(newValue));
                    String[] fps = String.valueOf(newValue).split("\\*");
                    MyLogUtil.e(TAG, "设置最小和最大预览fps=" + fps[0]+"*"+fps[1]);
                    camera1SettingsHelper.setPreviewFpsRange(Integer.valueOf(fps[0]), Integer.valueOf(fps[1]));
                    return true;
                }
            });

            //设置接收预览帧的速率
            ListPreference setPreviewFrameRate = findPreference("setPreviewFrameRate");
            List<Integer> getSupportedPreviewFrameRates = camera1SettingsHelper.getSupportedPreviewFrameRates();
            List<String> getSupportedPreviewFrameRatesEntry = new ArrayList<>();

            if (getSupportedPreviewFrameRates != null) {
                for (int i = 0; i < getSupportedPreviewFrameRates.size(); i++) {
                    getSupportedPreviewFrameRatesEntry.add(String.valueOf(getSupportedPreviewFrameRates.get(i)));
                    MyLogUtil.e(TAG, "支持的预览帧的速率=" + getSupportedPreviewFrameRatesEntry.get(i));
                }
                doDataBind(setPreviewFrameRate, getSupportedPreviewFrameRatesEntry,getSupportedPreviewFrameRatesEntry);
            }  else {
                setPreviewFrameRate.setEnabled(false);
                setPreviewFrameRate.setSummary("不支持");
            }
            setPreviewFrameRate.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setPreviewFrameRate.setSummary(String.valueOf(newValue));
                    MyLogUtil.e(TAG, "设置接收预览帧的速率=" + newValue);
                    camera1SettingsHelper.setPreviewFrameRate(Integer.valueOf(String.valueOf(newValue)));
                    return true;
                }
            });

            //设置图片的尺寸
            ListPreference setPreviewSize = findPreference("setPreviewSize");
            List<Camera.Size> getSupportedPreviewSizes = camera1SettingsHelper.getSupportedPreviewSizes();

            if (getSupportedPreviewSizes == null) {
                setPreviewSize.setEnabled(false);
                setPreviewSize.setSummary("不支持");
            } else {
                CharSequence mentries[] = new String[getSupportedPreviewSizes.size()];
                CharSequence mentryValues[] = new String[getSupportedPreviewSizes.size()];
                int i = 0;
                for (Camera.Size size : getSupportedPreviewSizes) {
                    mentries[i] = size.width + "*" + size.height;
                    mentryValues[i] = size.width + "*" + size.height;
                    i++;
                }
                setPreviewSize.setEntries(mentries);
                setPreviewSize.setEntryValues(mentryValues);
            }

            setPreviewSize.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    String[] size = String.valueOf(newValue).split("\\*");
                    MyLogUtil.e(TAG, "设置预览的尺寸=" + size[0] + "*" + size[1]);
                    setPreviewSize.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setPreviewSize(Integer.valueOf(size[0]), Integer.valueOf(size[1]));
                    return true;
                }
            });

            //设置录制模式
            SwitchPreference setRecordingHint = findPreference("setRecordingHint");
            setRecordingHint.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    MyLogUtil.e(TAG, "设置录制模式=" + newValue);
                    camera1SettingsHelper.setRecordingHint((boolean) newValue);
                    return true;//设置为true，按钮生效
                }
            });
            //设置相对于相机方向的顺时针旋转角度
            ListPreference setRotation = findPreference("setRotation");
            setRotation.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setRotation.setSummary((String) newValue);
                    MyLogUtil.e(TAG, "设置相对于相机方向的顺时针旋转角度=" + newValue);
                    camera1SettingsHelper.setRotation(Integer.parseInt(String.valueOf(newValue)));
                    return true;
                }
            });

            //设置对焦模式
            ListPreference setSceneMode = findPreference("setSceneMode");
            List<String> getSupportedSceneModes = camera1SettingsHelper.getSupportedSceneModes();
            String getSceneMode = camera1SettingsHelper.getSceneMode();
            MyLogUtil.e(TAG, "当前情景模式=" + getSceneMode);
            setSceneMode.setSummary(getSceneMode);
            doDataBind(setSceneMode, getSupportedSceneModes, getSupportedSceneModes);

            setSceneMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setSceneMode.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setSceneMode(String.valueOf(newValue));
                    return true;
                }
            });

            //设置录制模式
            SwitchPreference setVideoStabilization = findPreference("setVideoStabilization");
            Preference isVideoStabilizationSupportedPreference = findPreference("isVideoStabilizationSupported");
            boolean isVideoStabilizationSupported = camera1SettingsHelper.isVideoStabilizationSupported();
            isVideoStabilizationSupportedPreference.setEnabled(isVideoStabilizationSupported);
            isVideoStabilizationSupportedPreference.setSummary(isAutoExposureLockSupported ? "支持" : "不支持");
            setVideoStabilization.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    MyLogUtil.e(TAG, "启用和禁用视频稳定=" + newValue);
                    camera1SettingsHelper.setVideoStabilization((boolean) newValue);
                    return true;//设置为true，按钮生效
                }
            });

            //设置白平衡
            ListPreference setWhiteBalance = findPreference("setWhiteBalance");
            List<String> getSupportedWhiteBalance = camera1SettingsHelper.getSupportedWhiteBalance();
            String getWhiteBalance = camera1SettingsHelper.getWhiteBalance();
            MyLogUtil.e(TAG, "当前白平衡=" + getWhiteBalance);
            setWhiteBalance.setSummary(getWhiteBalance);
            doDataBind(setWhiteBalance, getSupportedWhiteBalance, getSupportedWhiteBalance);

            setWhiteBalance.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setWhiteBalance.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setWhiteBalance(String.valueOf(newValue));
                    return true;
                }
            });

            //设置缩放
            Preference isZoomSupportedPreference = findPreference("isZoomSupported");
            boolean isZoomSupported = camera1SettingsHelper.isZoomSupported();
            isZoomSupportedPreference.setEnabled(isZoomSupported);
            isZoomSupportedPreference.setSummary(isZoomSupported ? "支持" : "不支持");

            ListPreference setZoom = findPreference("setZoom");
            List<Integer> getZoomRatios = camera1SettingsHelper.getZoomRatios();
            List<String> getZoomRatiosEntry =new ArrayList<>();
            if(getZoomRatios!=null){
                for(int i=0;i<getZoomRatios.size();i++){
                    getZoomRatiosEntry.add(String.valueOf(getZoomRatios.get(i)));
                }
            }

            doDataBind(setZoom, getZoomRatiosEntry, getZoomRatiosEntry);

            setZoom.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setZoom.setSummary(String.valueOf(newValue));
                    camera1SettingsHelper.setZoom(Integer.parseInt(String.valueOf(newValue)));
                    return true;
                }
            });
        }
    }
}