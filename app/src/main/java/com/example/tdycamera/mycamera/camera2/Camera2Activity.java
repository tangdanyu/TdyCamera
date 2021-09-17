package com.example.tdycamera.mycamera.camera2;


import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.OrientationEventListener;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mnn.MNNFaceDetectListener;
import com.example.tdycamera.mnn.MNNFaceDetectorAdapter;
import com.example.tdycamera.mycamera.camera2.view.AutoFitTextureView;


public class Camera2Activity extends AppCompatActivity implements View.OnClickListener {
    private String TAG = "Camera2Activity";

//    private SurfaceHolder surfaceHolder;  // 用于画框的surfaceView的holder
//    private SurfaceView surfaceView;
    //人脸特征点画笔
    private Paint KeyPointsPaint;
    private Canvas canvas;     // 画布
    private int mRotateDegree; // 屏幕旋转角度：0/90/180/270
    private OrientationEventListener orientationListener;       // 监听屏幕旋转
//    private Size mPreviewSize;

    //阿里人脸识别工具类
    private MNNFaceDetectorAdapter mnnFaceDetectorAdapter;
    //阿里人脸识别
    private MNNFaceDetectListener mnnFaceDetectListener;

    //相机控制
    private Camera2Helper camera2Helper;
    //相机数据回调
    private CameraListener cameraListener;
    private Button startBtn;
    private Button stopBtn;
    private AutoFitTextureView autoFitTextureView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera2);

        initView();
        initListener();
        initData();
    }
    private void initView(){

        startBtn = findViewById(R.id.start_btn);
        stopBtn = findViewById(R.id.stop_btn);
        startBtn.setOnClickListener(this);
        stopBtn.setOnClickListener(this);

//        surfaceView = findViewById(R.id.surface_view);
        autoFitTextureView = findViewById(R.id.texture_view);
        // 设置SurfaceView
//        surfaceView.setZOrderOnTop(true);  // 设置surfaceView在顶层
//        surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT); // 设置surfaceView为透明
//        surfaceHolder = surfaceView.getHolder();  // 获取surfaceHolder以便后面画框
        // 初始化画框用的两个Paint和一个Canvas，避免在子线程中重复创建
        KeyPointsPaint = new Paint();  // 画矩形的paint
        KeyPointsPaint.setColor(Color.YELLOW);
        KeyPointsPaint.setStyle(Paint.Style.STROKE);//不填充
        KeyPointsPaint.setStrokeWidth(5); //线的宽度


        canvas = new Canvas();  // 画布
    }
    private void initListener(){

        cameraListener = new CameraListener() {
            @Override
            public void onCameraOpened(int width, int height) {
                // 设置画框用的surfaceView的展示尺寸，也是TextureView的展示尺寸（因为是竖屏，所以宽度比高度小）
//                surfaceHolder.setFixedSize(width,height);
//                mPreviewSize = new Size(width,height);
            }

            @Override
            public void onCameraClosed() {

            }

            @Override
            public void onCameraPreview(byte[] data, int width, int height, int displayOrientation) {
//                MyLogUtil.e(TAG,"拿到视频帧数据了"+" "+width+" "+height );
                FaceDetectionReport[] results = mnnFaceDetectorAdapter.getFace(data, width, height, 1, 270, 0, true);
                if (results != null) {
                    float[] rect = new float[4];
                    float[] points = new float[106 * 2];
                    //矩形框
                    rect[0] = results[0].rect.left;
                    rect[1] = results[0].rect.top;
                    rect[2] = results[0].rect.right;
                    rect[3] = results[0].rect.bottom;
                    //人脸特征点
                    for (int i = 0; i < 106; ++i) {
                        points[i * 2] = results[0].keyPoints[i * 2];
                        points[i * 2 + 1] = results[0].keyPoints[i * 2 + 1];
                    }
//                    drawResult(rect, results[0].keyPoints, width, height, displayOrientation);
                }


            }
        };
        mnnFaceDetectListener = new MNNFaceDetectListener() {
            @Override
            public void onNoFaceDetected() {

            }
        };
        mnnFaceDetectorAdapter = new MNNFaceDetectorAdapter( this , mnnFaceDetectListener);
    }
    private void initData(){
//        camera2Helper = new Camera2Helper(this,cameraListener,surfaceView);
        camera2Helper = new Camera2Helper(this,autoFitTextureView,cameraListener);

    }
    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        camera2Helper.onResume();

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
    @Override
    protected void onDestroy() {
        orientationListener.disable();
        if (mnnFaceDetectorAdapter != null) {
            mnnFaceDetectorAdapter.close();
            mnnFaceDetectorAdapter = null;
        }
        super.onDestroy();
    }
    @Override
    protected void onPause() {
        camera2Helper.onPause();
        super.onPause();
    }

    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器
    /**
     * 开启子线程
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.start_btn:
                camera2Helper.startRecord();
                break;
            case R.id.stop_btn:
                camera2Helper.stopRecord();
                break;
        }
    }

//    // HrHelper调用onDrawResult，绘制特征点
//    private void drawResult(float[] rect, float[] facePoints, int width, int height, int cameraOrientation) {
//
//        try {
//            canvas = surfaceHolder.lockCanvas();
//            if (canvas == null) {
//                MyLogUtil.e("canvas == null");
//                return;
//            }
//            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//
//            if(mRotateDegree != 0){
//                    if(mRotateDegree == 270){
//                        canvas.translate(mPreviewSize.getHeight(),0); // 坐标原点在x轴方向移动屏幕宽度的距离
//                        canvas.rotate(90);   // canvas顺时针旋转90°
//                    } else if(mRotateDegree == 90){
//                        canvas.translate(0,mPreviewSize.getWidth());
//                        canvas.rotate(-90);
//                    } else if(mRotateDegree == 180){
//                        canvas.translate(mPreviewSize.getHeight(),mPreviewSize.getWidth());
//                        canvas.rotate(180);
//                    }
//                }
//
//            // 绘制人脸关键点
//            for (int j = 0; j < 106; j++) {
//                float keyX = facePoints[j * 2];
//                float keyY = facePoints[j * 2 + 1];
//                canvas.drawCircle(keyX , keyY , 4.0f, KeyPointsPaint);
//            }
//            float left = rect[0];
//            float top = rect[1];
//            float right = rect[2];
//            float bottom = rect[3];
//            canvas.drawLine(left , top ,
//                    right , top  , KeyPointsPaint);
//            canvas.drawLine(right  , top ,
//                    right  , bottom  , KeyPointsPaint);
//            canvas.drawLine(right  , bottom ,
//                    left  , bottom  , KeyPointsPaint);
//            canvas.drawLine(left  , bottom ,
//                    left  , top , KeyPointsPaint);
//
//        } catch (Throwable t) {
//            MyLogUtil.e(TAG, "Draw result error: %s" + t);
//        } finally {
//            if (canvas != null) {
//                surfaceHolder.unlockCanvasAndPost(canvas);
//            }
//        }
//    }
//
//    // HrHelper调用onDrawClear，清除特征点
//    private void drawClear() {
//
//        try {
//            canvas = surfaceHolder.lockCanvas();
//            if (canvas == null) {
//                MyLogUtil.e("canvas == null");
//                return;
//            }
//            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//        } catch (Throwable t) {
//            MyLogUtil.e(TAG, "Draw result error: %s" + t);
//        } finally {
//            if (canvas != null) {
//                surfaceHolder.unlockCanvasAndPost(canvas);
//            }
//        }
//    }
//    /**
//     * 接收识别结果进行画框
//     * @param get_finalResult 识别的结果数组，包含物体名称name、置信度confidence和用于画矩形的参数（x,y,width,height）
//     *            name=floats[0]  confidence=floats[1]  x=floats[2] y=floats[3] width=floats[4] height=floats[5]
//     */
//    private void show_detect_results(final float[][] get_finalResult) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                ClearDraw();   // 先清空上次画的框
//
//                canvas = surfaceHolder.lockCanvas();   // 得到surfaceView的画布
//                // 根据屏幕旋转角度调整canvas，以使画框方向正确
//                if(mRotateDegree != 0){
//                    if(mRotateDegree == 270){
//                        canvas.translate(mPreviewSize.getHeight(),0); // 坐标原点在x轴方向移动屏幕宽度的距离
//                        canvas.rotate(90);   // canvas顺时针旋转90°
//                    } else if(mRotateDegree == 90){
//                        canvas.translate(0,mPreviewSize.getWidth());
//                        canvas.rotate(-90);
//                    } else if(mRotateDegree == 180){
//                        canvas.translate(mPreviewSize.getHeight(),mPreviewSize.getWidth());
//                        canvas.rotate(180);
//                    }
//                }
//                for (float[] floats : get_finalResult) {   // 画框并在框上方输出识别结果和置信度
//                    canvas.drawRect(floats[2], floats[3],
//                            floats[2] + floats[4],
//                            floats[3] + floats[5], paint_rect);
//                    canvas.drawText(resultLabel.get((int) floats[0]) + "\n" + floats[1],
//                            floats[2], floats[3], paint_txt);
//                }
//                surfaceHolder.unlockCanvasAndPost(canvas);  // 释放
//            }
//        });
//    }
//
//    /**
//     * 清空上次的框
//     */
//    private void ClearDraw(){
//        try{
//            canvas = surfaceHolder.lockCanvas(null);
//            canvas.drawColor(Color.WHITE);
//            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.SRC);
//        }catch(Exception e){
//            e.printStackTrace();
//        }finally{
//            if(canvas != null){
//                surfaceHolder.unlockCanvasAndPost(canvas);
//            }
//        }
//    }

}