package com.example.tdycamera.mycamera.camera1;

import android.hardware.Camera;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.example.tdycamera.R;
import com.example.tdycamera.utils.MyLogUtil;

import java.util.ArrayList;
import java.util.List;

public class Camera1SettingsActivity extends AppCompatActivity {
    private static String TAG = "Camera1SettingsActivity";

    private static Camera1SettingsHelper camera1SettingsHelper;
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

    private static void doDataBind(ListPreference preference, List<String> list){
        if(list== null){
            preference.setEnabled(false);
            preference.setSummary("不支持");
        }else {
            CharSequence mentries[] = new String[list.size()];
            CharSequence mentryValues[] = new String[list.size()];
            int i = 0;
            for (String mdata : list) {
                mentries[i] = mdata;
                mentryValues[i] = mdata;
                i++;
            }
            preference.setEntries(mentries);
            preference.setEntryValues(mentryValues);
        }
    }
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            //是否支持自动曝光锁定
            Preference preferenceIsAutoExposureLockSupported = findPreference("isAutoExposureLockSupported");
            boolean isAutoExposureLockSupported = camera1SettingsHelper.isAutoExposureLockSupported();
            MyLogUtil.e(TAG,"是否支持自动曝光锁定="+isAutoExposureLockSupported);
            preferenceIsAutoExposureLockSupported.setEnabled(isAutoExposureLockSupported);
            preferenceIsAutoExposureLockSupported.setSummary(isAutoExposureLockSupported ? "支持":"不支持");

            if(isAutoExposureLockSupported){
                //设置自动曝光锁定状态
                SwitchPreferenceCompat switchAutoExposureLock = findPreference("setAutoExposureLock");
                switchAutoExposureLock.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MyLogUtil.e(TAG,"是否锁定"+newValue);
                        camera1SettingsHelper.setAutoExposureLock((boolean) newValue);
                        return true;//设置为true，按钮生效
                    }
                });
            }

            //是否支持自动白平衡锁定
            Preference preferenceAutoWhiteBalanceLock = findPreference("isAutoWhiteBalanceLockSupported");
            boolean isAutoWhiteBalanceLockSupported = camera1SettingsHelper.isAutoWhiteBalanceLockSupported();
            MyLogUtil.e(TAG,"是否支持自动白平衡锁定="+isAutoWhiteBalanceLockSupported);
            preferenceAutoWhiteBalanceLock.setEnabled(isAutoWhiteBalanceLockSupported);
            preferenceAutoWhiteBalanceLock.setSummary(isAutoWhiteBalanceLockSupported ? "支持":"不支持");

            if(isAutoWhiteBalanceLockSupported){
                //设置自动白平衡锁定状态
                SwitchPreferenceCompat switchAutoWhiteBalanceLock = findPreference("setAutoWhiteBalanceLock");
                switchAutoWhiteBalanceLock.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        MyLogUtil.e(TAG,"是否锁定="+newValue);
                        camera1SettingsHelper.setAutoWhiteBalanceLock((boolean) newValue);
                        return true;//设置为true，按钮生效
                    }
                });
            }



            //获得支持的色彩效果设置
            ListPreference setColorEffect = findPreference("setColorEffect");
            List<String> SupportedColorEffects = camera1SettingsHelper.getSupportedColorEffects();
            String ColorEffect = camera1SettingsHelper.getColorEffect();
            MyLogUtil.e(TAG,"获取当前的色彩效果设置="+ColorEffect);
            setColorEffect.setSummary(ColorEffect == null? "无":ColorEffect );
            doDataBind(setColorEffect,SupportedColorEffects);

            //设置相机曝光补偿指数
            ListPreference setExposureCompensation = findPreference("setExposureCompensation");
            //最大曝光补偿指数
            int getMaxExposureCompensation = camera1SettingsHelper.getMaxExposureCompensation();
            //最小曝光补偿指数
            int getMinExposureCompensation = camera1SettingsHelper.getMinExposureCompensation();
            MyLogUtil.e(TAG,"最大曝光补偿指数="+getMaxExposureCompensation);
            MyLogUtil.e(TAG,"最小曝光补偿指数="+getMinExposureCompensation);
            List<String> exposureList = new ArrayList<>();
            for(int i = getMinExposureCompensation ;i<= getMaxExposureCompensation;i++){
                exposureList.add(String.valueOf(i));
            }
            int getExposureCompensation = camera1SettingsHelper.getExposureCompensation();
            MyLogUtil.e(TAG,"当前曝光补偿指数="+getExposureCompensation);
            setExposureCompensation.setSummary(String.valueOf(getExposureCompensation));
            doDataBind(setExposureCompensation,exposureList);
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
            MyLogUtil.e(TAG,"获取当前的闪光模式设置="+getFlashMode);
            if(getFlashMode!= null){
                setFlashMode.setSummary(getFlashMode);
            }
            doDataBind(setFlashMode,getSupportedFlashModes);

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
            MyLogUtil.e(TAG,"当前对焦模式="+getFocusMode);
            setFocusMode.setSummary(getFocusMode);
            doDataBind(setFocusMode,getSupportedFocusModes);

            setFocusMode.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    setFocusMode.setSummary(String.valueOf(newValue));
                    return true;
                }
            });

            //

        }
    }
}