package com.example.tdycamera.mycamera.opencv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
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

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class OpencvCamera1Activity extends CameraActivity implements CameraBridgeViewBase.CvCameraViewListener, View.OnClickListener {
    private static final String TAG = "OpencvCamera1Activity";
    /****阿里Mnn相关*****/
    private MNNFaceDetectorAdapter mnnFaceDetectorAdapter;  //阿里人脸识别工具类
    private MNNFaceDetectListener mnnFaceDetectListener;    //阿里人脸识别
    private MNNDrawUtil mnnDrawUtil;//特征点的绘制
    private Activity activity;

    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转

    //相机控制
    private OpencvCamera1Helper mOpenCvCameraView;
    //相机数据回调
    private CameraListener cameraListener;
    //相机预览控件
//    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView previewIv;

    private Button switchCameraBtn;
    private Button settingBtn;
    private Button takePictureBtn;
    private Button recordBtn;

    Mat mRgba;
    Mat mRgbaF;
    Mat mRgbaT;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    MyLogUtil.e(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
//        mOpenCvCameraView = (CameraBridgeViewBase) new JavaCameraView(this, -1);
//        setContentView(mOpenCvCameraView);
//        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        setContentView(R.layout.activity_opencv_camera);
        activity = this;
        initView();
        initListener();
        initData();
    }

    private void initView() {
        mOpenCvCameraView = findViewById(R.id.opencv_camera);
        previewIv = findViewById(R.id.preview_iv);
        switchCameraBtn = findViewById(R.id.switch_camera_btn);
        settingBtn = findViewById(R.id.setting_btn);
        takePictureBtn = findViewById(R.id.take_picture_btn);
        recordBtn = findViewById(R.id.record_btn);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(1);//0为后置，1为前置
    }

    private void initListener() {
        switchCameraBtn.setOnClickListener(this);
        settingBtn.setOnClickListener(this);
        takePictureBtn.setOnClickListener(this);
        recordBtn.setOnClickListener(this);
        cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(int width, int height, int displayOrientation) {
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
                if (width <= 0 || height <= 0) {
                    return;
                }

                // 输入角度
                int inAngle = mOpenCvCameraView.isFrontCamera() ? (displayOrientation + 360 - mRotateDegree) % 360 : (displayOrientation + mRotateDegree) % 360;
                // 输出角度
                int outAngle = 0;

                if (!screenAutoRotate()) {
                    outAngle = mOpenCvCameraView.isFrontCamera() ? (360 - mRotateDegree) % 360 : mRotateDegree % 360;
                }
//                MyLogUtil.e(TAG,"MNN"+" data="+data.length+" displayOrientation="+displayOrientation+" inAngle="+inAngle+" outAngle="+outAngle);//data=2764800 displayOrientation=270 inAngle=270 outAngle=0
                FaceDetectionReport[] results = mnnFaceDetectorAdapter.getFace(data, width, height,  MNNCVImageFormat.YUV_NV21.format, inAngle, outAngle, mOpenCvCameraView.isFrontCamera());
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
        mnnFaceDetectorAdapter = new MNNFaceDetectorAdapter(this, mnnFaceDetectListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            MyLogUtil.e(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            MyLogUtil.e(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    protected void onDestroy() {
        orientationListener.disable();
        if (mnnFaceDetectorAdapter != null) {
            mnnFaceDetectorAdapter.close();
            mnnFaceDetectorAdapter = null;
        }
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.switch_camera_btn:
                if (mOpenCvCameraView != null) {
                    mOpenCvCameraView.switchCamera();
                }
                break;
            case R.id.setting_btn:
                break;
            case R.id.take_picture_btn:
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                String currentDateandTime = sdf.format(new Date());
                String fileName = Environment.getExternalStorageDirectory().getPath() +
                        "/sample_picture_" + currentDateandTime + ".jpg";
                mOpenCvCameraView.takePicture(fileName);
                Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show();
                MyLogUtil.e(TAG,"拍照="+fileName);
                break;
            case R.id.record_btn:
//                if (mOpenCvCameraView != null) {
//                    if (mOpenCvCameraView.isRecordVideo()) {
//                        if (mOpenCvCameraView.isRecording()) {
//                            recordBtn.setText("开始录制");
//                            mOpenCvCameraView.stopRecord();
//                        } else {
//                            recordBtn.setText("结束录制");
//                            mOpenCvCameraView.startRecord();
//                        }
//                    } else {
//                        Toast.makeText(this, "没有开启录像", Toast.LENGTH_SHORT);
//                    }
//                }
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

    public void onCameraViewStarted(int width, int height) {
        MyLogUtil.e(TAG, "width=" + width + " height=" + height);// width=960 height=720
        cameraListener.onCameraOpened(width, height, mOpenCvCameraView.getDisplay().getRotation());
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
    }


    public Mat onCameraFrame(Mat inputFrame) {
//        MyLogUtil.e(TAG,"数据类型"+inputFrame.type());//24 CV_8UC4
        // width=960 height=720
//        Bitmap bitmap = Bitmap.createBitmap(inputFrame.width(), inputFrame.height(), Bitmap.Config.ARGB_8888);
//        Utils.matToBitmap(inputFrame, bitmap, true);//添加透明度
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                previewIv.setVisibility(View.VISIBLE);
//                previewIv.setImageBitmap(bitmap);
//                previewIv.setImageBitmap(ImageUtil.nv21ToBitmap(nv21, inputFrame.width(), inputFrame.height(), getBaseContext()));
//            }
//        });
        if (mOpenCvCameraView.isFrontCamera()) {
            Core.flip(inputFrame, inputFrame, 1);//解决前置倒置问题
        }

        return inputFrame;
    }

}