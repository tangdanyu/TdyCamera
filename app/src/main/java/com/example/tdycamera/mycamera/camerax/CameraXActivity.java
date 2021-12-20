package com.example.tdycamera.mycamera.camerax;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.alibaba.android.mnnkit.entity.MNNCVImageFormat;
import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mnn.MNNDrawUtil;
import com.example.tdycamera.mnn.MNNFaceDetectListener;
import com.example.tdycamera.mnn.MNNFaceDetectorAdapter;
import com.example.tdycamera.utils.MyLogUtil;

import androidx.camera.view.PreviewView;

public class CameraXActivity extends AppCompatActivity implements View.OnClickListener {
    private String TAG = "CameraXActivity";
    /****阿里Mnn相关*****/
    private MNNFaceDetectorAdapter mnnFaceDetectorAdapter;  //阿里人脸识别工具类
    private MNNFaceDetectListener mnnFaceDetectListener;    //阿里人脸识别
    private MNNDrawUtil mnnDrawUtil;//特征点的绘制
    private Activity activity;

    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转

    //相机控制
    private CameraXHelper cameraXHelper;
    //相机数据回调
    private CameraListener cameraListener;
    //相机预览控件
    private PreviewView previewView;
    private ImageView previewIv;

    private long lastTime = System.currentTimeMillis();

    private Button switchCameraBtn;
    private Button settingBtn;
    private Button takePictureBtn;
    private Button recordBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camerax);
        activity = this;
        initView();
        initListener();
        initData();
    }

    private void initView() {
        previewView = findViewById(R.id.previewView);
        previewIv = findViewById(R.id.preview_iv);
        switchCameraBtn = findViewById(R.id.switch_camera_btn);
        settingBtn = findViewById(R.id.setting_btn);
        takePictureBtn = findViewById(R.id.take_picture_btn);
        recordBtn = findViewById(R.id.record_btn);
    }

    private void initListener() {
        switchCameraBtn.setOnClickListener(this);
        settingBtn.setOnClickListener(this);
        takePictureBtn.setOnClickListener(this);
        recordBtn.setOnClickListener(this);
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
            public void onCameraPreview(byte[] data, int width, int height, int displayOrientation) {
//                MyLogUtil.e(TAG,"时间"+(System.currentTimeMillis() - lastTime)+" width"+width+" height"+height+" Orientation"+displayOrientation);//width1920 height960 Orientation270
                lastTime = System.currentTimeMillis();
                if (width <= 0 || height <= 0) {
                    return;
                }

                if (mnnDrawUtil == null) {
                    cameraListener.onCameraOpened(width, height, displayOrientation);
                }
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        previewIv.setImageBitmap(ImageUtil.nv21ToBitmap(data, width, height, getBaseContext()));
//                    }
//                });

                // 输入角度
                int inAngle = cameraXHelper.isFrontCamera() ? (displayOrientation + 360 - mRotateDegree) % 360 : (displayOrientation + mRotateDegree) % 360;
                // 输出角度
                int outAngle = 0;

                if (!screenAutoRotate()) {
                    outAngle = cameraXHelper.isFrontCamera() ? (360 - mRotateDegree) % 360 : mRotateDegree % 360;
                }
//                MyLogUtil.e(TAG,"MNN"+" data="+data.length+" displayOrientation="+displayOrientation+" inAngle="+inAngle+" outAngle="+outAngle);//data=2764800 displayOrientation=270 inAngle=270 outAngle=0
                FaceDetectionReport[] results = mnnFaceDetectorAdapter.getFace(data, width, height,  MNNCVImageFormat.YUV_NV21.format, inAngle, outAngle, cameraXHelper.isFrontCamera());
                if (mnnDrawUtil != null) {
                    if (results != null) {
                        mnnDrawUtil.drawResult(displayOrientation, mRotateDegree, results);
                    } else {
                        mnnDrawUtil.drawClear();
                    }

                }
            }

            @Override
            public void onPictureTaken(byte[] data) {
                Bitmap bitmap = null;
                try {
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bitmap != null) {
                        Bitmap newImage = null;
                        if (cameraXHelper.isFrontCamera()) {
                            MyLogUtil.e(TAG, "前置");
                            //使用矩阵反转图像数据并保持其正常
                            Matrix mtx = new Matrix();
                            //这将防止镜像
                            mtx.preScale(-1.0f, 1.0f);
                            //将post rotate设置为90，因为图像可能位于横向
//                            mtx.postRotate(90.f);
                            //旋转位图，创建我们想要的真实图像
                            newImage = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mtx, true);
                        } else {// LANDSCAPE MODE
                            MyLogUtil.e(TAG, "后置");
                            //不需要反转宽度和高度
                            newImage = bitmap;
                        }
                        previewIv.setVisibility(View.VISIBLE);
                        previewIv.setImageBitmap(newImage);
                    }
                } catch (OutOfMemoryError e) {
                    MyLogUtil.e(TAG, "Out of memory decoding image from camera." + e);
                    return;
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
        cameraXHelper = new CameraXHelper(this, previewView, cameraListener);
        mnnFaceDetectorAdapter = new MNNFaceDetectorAdapter(this, mnnFaceDetectListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        //请求相机权限
    }

    @Override
    protected void onDestroy() {
        orientationListener.disable();
        if (mnnFaceDetectorAdapter != null) {
            mnnFaceDetectorAdapter.close();
            mnnFaceDetectorAdapter = null;
        }
        super.onDestroy();
        if (cameraXHelper != null) {
//            cameraXHelper.onDestroy();
            cameraXHelper = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            //Android Q内存泄漏问题的解决方法。
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_camera_btn:
                //切换摄像头，要先解绑生命周期再开启
                MyLogUtil.e(TAG, "切换摄像头");
                if (cameraXHelper != null) {
                    cameraXHelper.switchCamera();
                }
                break;
            case R.id.setting_btn:

                break;
            case R.id.take_picture_btn:
                if (cameraXHelper != null) {
                    cameraXHelper.takePicture();
                }
                break;
            case R.id.record_btn:
                if (cameraXHelper != null) {
                    if (cameraXHelper.isRecordVideo()) {
                        if (cameraXHelper.isRecording()) {
                            recordBtn.setText("开始录制");
                            cameraXHelper.stopRecord();
                        } else {
                            recordBtn.setText("结束录制");
                            cameraXHelper.startRecord();
                        }
                    } else {
                        Toast.makeText(this, "没有开启录像", Toast.LENGTH_SHORT);
                    }
                }
                break;
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