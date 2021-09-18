package com.example.tdycamera.mycamera.camera1;

import android.app.Activity;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mnn.MNNDrawUtil;
import com.example.tdycamera.mnn.MNNFaceDetectListener;
import com.example.tdycamera.mnn.MNNFaceDetectorAdapter;
import com.example.tdycamera.utils.ImageUtil;

public class Camera1Activity extends AppCompatActivity {
    private String TAG = "Camera1Activity";
    /****阿里Mnn相关*****/
    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转
    private MNNFaceDetectorAdapter mnnFaceDetectorAdapter;  //阿里人脸识别工具类
    private MNNFaceDetectListener mnnFaceDetectListener;    //阿里人脸识别
    private MNNDrawUtil mnnDrawUtil;//特征点的绘制

    //相机控制
    private Camera1Helper camera1Helper;
    //相机数据回调
    private CameraListener cameraListener;
    //相机预览控件
    private SurfaceView surfaceView;
    private ImageView previewIv;
    private Activity activity;
    private long lastTime = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera1);
        activity = this;
        initView();
        initListener();
        initData();
    }

    private void initView() {
        surfaceView = findViewById(R.id.surface_view);
        previewIv = findViewById(R.id.preview_iv);

    }

    private void initListener() {
        cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(int width, int height, int displayOrientation) {
                // 设置画框用的surfaceView的展示尺寸，也是TextureView的展示尺寸（因为是竖屏，所以宽度比高度小）

//                MyLogUtil.e(TAG,"width"+width+"height"+height);
//                MyLogUtil.e(TAG, "getMeasuredWidth"+autoFitTextureView.getMeasuredWidth()+"getMeasuredHeight"+autoFitTextureView.getMeasuredHeight());
                if (displayOrientation == 0 || displayOrientation == 180) {
                    mnnDrawUtil = new MNNDrawUtil(activity,
                            width, height, height, width, screenAutoRotate());
                } else {
                    mnnDrawUtil = new MNNDrawUtil(activity,
                            width, height, width, height, screenAutoRotate());
                }
            }

            @Override
            public void onCameraClosed() {
            }

            @Override
            public void onCameraPreview(byte[] data, int width, int height, int displayOrientation) {
//                MyLogUtil.e(TAG,"onCameraPreview width "+width+" height "+height+ " displayOrientation"+displayOrientation);
                lastTime = System.currentTimeMillis();
                if (width <= 0 || height <= 0) {
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        previewIv.setImageBitmap(ImageUtil.nv21ToBitmap(data, width, height, getBaseContext()));
                    }
                });

                // 输入角度
                int inAngle = camera1Helper.isFrontCamera() ? (displayOrientation + 360 - mRotateDegree) % 360 : (displayOrientation + mRotateDegree) % 360;
                // 输出角度
                int outAngle = 0;

                if (!screenAutoRotate()) {
                    outAngle = camera1Helper.isFrontCamera() ? (360 - mRotateDegree) % 360 : mRotateDegree % 360;
                }
//                MyLogUtil.e(TAG,"MNN"+" data"+data.length+" inAngle"+inAngle+" outAngle"+outAngle);
                FaceDetectionReport[] results = mnnFaceDetectorAdapter.getFace(data, width, height, 1, inAngle, outAngle, true);
                if (results != null) {
                    mnnDrawUtil.drawResult(displayOrientation, mRotateDegree, results);
                } else {
                    mnnDrawUtil.drawClear();
                }
            }
        };

        mnnFaceDetectListener = new MNNFaceDetectListener() {
            @Override
            public void onNoFaceDetected() {
            }
        };

        // 监听屏幕的转动，mRotateDegree有四个值：0/90/180/270,0是平常的竖屏，然后依次顺时针旋转90°得到后三个值
        orientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {

                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                    return;  //手机平放时，检测不到有效的角度
                }

                //可以根据不同角度检测处理，这里只检测四个角度的改变
                // 可以扩展到多于四个的检测，以在不同角度都可以画出完美的框
                // （要在对应的画框处添加多余角度的Canvas的旋转）
                orientation = (orientation + 45) / 90 * 90;
                mRotateDegree = orientation % 360;
                //Log.d(TAG, "mRotateDegree: "+mRotateDegree);
            }
        };

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable();   // 开启此监听
        } else {
            orientationListener.disable();
        }
    }

    private void initData() {
        camera1Helper = new Camera1Helper(this, cameraListener, surfaceView);
        mnnFaceDetectorAdapter = new MNNFaceDetectorAdapter(this, mnnFaceDetectListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera1Helper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera1Helper.onPause();
    }

    @Override
    protected void onDestroy() {
        orientationListener.disable();
        if (mnnFaceDetectorAdapter != null) {
            mnnFaceDetectorAdapter.close();
            mnnFaceDetectorAdapter = null;
        }
        super.onDestroy();
        if (camera1Helper != null) {
            camera1Helper.onDestroy();
            camera1Helper = null;
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
}