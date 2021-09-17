package com.example.tdycamera.mycamera.camera1;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.SurfaceView;

import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;

public class Camera1Activity extends AppCompatActivity {
    private String TAG = "Camera1Activity";
    //相机预览
    private Camera1Helper camera1Helper;
    //相机回调
    private CameraListener cameraListener;
    //预览控件
    private SurfaceView surfaceView;
    //设备旋转监听
    private OrientationEventListener mOrientationListener;
    //设备旋转角度：0/90/180/360
    protected int rotateDegree;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        detectScreenRotate();
        setContentView(R.layout.activity_camera1);
        initView();
        initListener();
    }

    private void initView(){
        surfaceView = findViewById(R.id.surface_view);
        camera1Helper = new Camera1Helper(this, cameraListener, surfaceView);
    }
    private void initListener(){
        cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(int width, int height) {
                MyLogUtil.e(TAG, "onCameraOpened width" + width + "height" + height);//1920*960

            }

            @Override
            public void onCameraClosed() {
            }

            @Override
            public void onCameraPreview(byte[] data, int width, int height, int displayOrientation) {
//                MyLogUtil.e(TAG,"onCameraPreview width "+width+" height "+height+ " displayOrientation"+displayOrientation);
                if (width <= 0 || height <= 0) {
                    return;
                }

                // 输入角度
                int inAngle = camera1Helper.isFrontCamera() ? (displayOrientation + 360 - rotateDegree) % 360 : (displayOrientation + rotateDegree) % 360;
                // 输出角度
                int outAngle = 0;
                if (!screenAutoRotate()) {
                    outAngle = camera1Helper.isFrontCamera() ? (360 - rotateDegree) % 360 : rotateDegree % 360;
                }
            }
        };
    }
    // 监听屏幕旋转
    private void detectScreenRotate() {
        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;  //手机平放时，检测不到有效的角度
                }
                //可以根据不同角度检测处理，这里只检测四个角度的改变
                orientation = (orientation + 45) / 90 * 90;
                if (screenAutoRotate() && orientation % 360 == 180) {
                    return;
                }
                rotateDegree = orientation % 360;
            }
        };
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }
    }
    // 系统是否开启自动旋转
    protected boolean screenAutoRotate() {

        boolean autoRotate = false;
        try {
            autoRotate = 1 == Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        return autoRotate;
    }
    @Override
    protected void onPause() {
        camera1Helper.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera1Helper.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera1Helper != null) {
            camera1Helper.onDestroy();
            camera1Helper = null;
        }
    }

}