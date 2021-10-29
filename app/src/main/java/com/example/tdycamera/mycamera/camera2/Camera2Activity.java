package com.example.tdycamera.mycamera.camera2;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Activity;
import android.content.Intent;
import android.hardware.SensorManager;
import android.media.Image;
import android.os.Bundle;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.ImageView;

import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mnn.MNNDrawUtil;
import com.example.tdycamera.mnn.MNNFaceDetectListener;
import com.example.tdycamera.mnn.MNNFaceDetectorAdapter;
import com.example.tdycamera.utils.ImageUtil;
import com.example.tdycamera.utils.MyLogUtil;
import com.example.tdycamera.view.AutoFitTextureView;

import android.widget.Button;
import android.view.View;

import java.nio.ByteBuffer;

public class Camera2Activity extends AppCompatActivity implements View.OnClickListener {
    private String TAG = "Camera2Activity";

    /****阿里Mnn相关*****/
    private MNNFaceDetectorAdapter mnnFaceDetectorAdapter;  //阿里人脸识别工具类
    private MNNFaceDetectListener mnnFaceDetectListener;    //阿里人脸识别
    private MNNDrawUtil mnnDrawUtil;//特征点的绘制
    private Activity activity;

    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转

    //相机控制
    private Camera2Helper camera2Helper;
    //相机数据回调
    private CameraListener cameraListener;
    //相机预览控件
    private SurfaceView surfaceView;
    private AutoFitTextureView autoFitTextureView;
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
        setContentView(R.layout.activity_camera2);
        activity = this;
        initView();
        initListener();
        initData();
    }

    private void initView() {
        autoFitTextureView = findViewById(R.id.texture_view);
        surfaceView = findViewById(R.id.surface_view);
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
            public void onPreviewFrame(Image image,int width,int height,int displayOrientation){
                lastTime = System.currentTimeMillis();
                ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
                ByteBuffer bufferU = image.getPlanes()[1].getBuffer();
                ByteBuffer bufferV = image.getPlanes()[2].getBuffer();

                ByteBuffer yuvbuffer = ByteBuffer.allocateDirect(bufferY.remaining() + bufferU.remaining() + bufferV.remaining());
                yuvbuffer.put(bufferY);
                yuvbuffer.put(bufferV);
                yuvbuffer.put(bufferU);
                image.close();

                byte[] data = yuvbuffer.array();

                MyLogUtil.e(TAG,"耗时="+(System.currentTimeMillis() - lastTime));//7ms
                int inAngle = camera2Helper.isFrontCamera() ? (displayOrientation + 360 - mRotateDegree) % 360 : (displayOrientation + mRotateDegree) % 360;
                int outAngle = 0;
                if (!screenAutoRotate()) {
                    outAngle = camera2Helper.isFrontCamera() ? (360 - mRotateDegree) % 360 : mRotateDegree % 360;
                }
                FaceDetectionReport[] results = mnnFaceDetectorAdapter.getFace(data, width, height, 1, inAngle, outAngle, true);
                if (results != null) {
                    mnnDrawUtil.drawResult(displayOrientation, mRotateDegree, results);
                } else {
                    mnnDrawUtil.drawClear();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        previewIv.setImageBitmap(ImageUtil.nv21ToBitmap(data, width, height, getBaseContext()));
                    }
                });

            }

            @Override
            public void onCameraPreview(byte[] data, int width, int height, int displayOrientation) {
//                MyLogUtil.e(TAG,"时间"+(System.currentTimeMillis() - lastTime)+" width"+width+" height"+height+" Orientation"+displayOrientation);//width1920 height960 Orientation270
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
                int inAngle = camera2Helper.isFrontCamera() ? (displayOrientation + 360 - mRotateDegree) % 360 : (displayOrientation + mRotateDegree) % 360;
                // 输出角度
                int outAngle = 0;

                if (!screenAutoRotate()) {
                    outAngle = camera2Helper.isFrontCamera() ? (360 - mRotateDegree) % 360 : mRotateDegree % 360;
                }
//                MyLogUtil.e(TAG,"MNN"+" data="+data.length+" displayOrientation="+displayOrientation+" inAngle="+inAngle+" outAngle="+outAngle);//data=2764800 displayOrientation=270 inAngle=270 outAngle=0
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
        camera2Helper = new Camera2Helper(this, autoFitTextureView, cameraListener);
        mnnFaceDetectorAdapter = new MNNFaceDetectorAdapter(this, mnnFaceDetectListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        camera2Helper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        camera2Helper.onPause();
    }

    @Override
    protected void onDestroy() {
        orientationListener.disable();
        if (mnnFaceDetectorAdapter != null) {
            mnnFaceDetectorAdapter.close();
            mnnFaceDetectorAdapter = null;
        }
        super.onDestroy();
        if (camera2Helper != null) {
            camera2Helper.onDestroy();
            camera2Helper = null;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_camera_btn:
                if(camera2Helper!= null){
                    camera2Helper.switchCamera();
                }
                break;
            case R.id.setting_btn:
//                startActivity(new Intent(getBaseContext(), Camera1SettingsActivity.class));
                break;
            case R.id.take_picture_btn:
                if(camera2Helper!= null) {
                    camera2Helper.takePicture();
                }
                break;
            case R.id.record_btn:
                if(camera2Helper!= null) {
                    if (camera2Helper.isRecording()) {
                        recordBtn.setText("开始录制");
                        camera2Helper.stopRecord();
                    } else {
                        recordBtn.setText("结束录制");
                        camera2Helper.startRecord();
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