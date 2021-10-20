package com.example.tdycamera.mycamera.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;
import com.example.tdycamera.view.AutoFitTextureView;
import com.example.yuvlib.YUVUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Helper {
    private static final String TAG = "Camera2Helper";
    private CameraManager cameraManager;//相机管理类
    private CameraCharacteristics cameraCharacteristics;//相机特性

    private Context mContext;
    private String mCameraId;                    //正在使用的相机id
    private Size mPreviewSize;                   // 预览数据的尺寸
    private int[] formats;//设备支持的格式
    private int hardwareLevel;//硬件级别
    private Range<Integer>[] fpsRanges;   // 相机的FPS范围
    private int mSensorOrientation;//相机传感器的方向
    private int facing = CameraCharacteristics.LENS_FACING_FRONT;//朝向
    private CameraCaptureSession mCaptureSession;// 预览用的获取会话
    private CameraDevice mCameraDevice;          // 正在使用的相机实例

    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;
    private CaptureRequest.Builder mPreviewRequestBuilder;  // 预览请求构建器
    private CaptureRequest mPreviewRequest;      // 预览请求, 由上面的构建器构建出来
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);  // 信号量控制器
    private ImageReader mImageReader;            // 用于获取画面的数据，并进行识别 YUV_420_888
    private int imageFormat = ImageFormat.YUV_420_888;
    private int format = ImageFormat.NV21;

    private AutoFitTextureView mTextureView;  // 预览使用的自定义TextureView控件
    private boolean isFrontCamera = true;               //是否前置摄像头

    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器

    // Camera2 API提供的最大预览宽度和高度
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private CameraListener mCameraListener;


    public Camera2Helper(Context context, AutoFitTextureView mTextureView, CameraListener cameraListener) {
        this.mContext = context;
        this.mTextureView = mTextureView;
        this.mCameraListener = cameraListener;
        cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        initMediaRecorder();
    }

    //切换相机
    public void switchCamera() {
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            facing = CameraCharacteristics.LENS_FACING_BACK;
            isFrontCamera = false;
        } else {
            facing = CameraCharacteristics.LENS_FACING_FRONT;
            isFrontCamera = true;
        }
        closeCamera();
        openCamera();
    }

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    //SurfaceTexture监听器
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);// width=1080, height=2160
            openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    //相机状态改变的回调函数
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            MyLogUtil.e(TAG, "onOpened");
            // 当相机打开执行以下操作:
            mCameraOpenCloseLock.release();  // 1. 释放访问许可
            mCameraDevice = cameraDevice;   // 2. 将正在使用的相机指向将打开的相机
            createPreviewAndRecordAndImageReaderSession();   // 3. 创建相机预览会话
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            MyLogUtil.e(TAG, "onDisconnected");
            // 当相机失去连接时执行以下操作:
            mCameraOpenCloseLock.release();   // 1. 释放访问许可
            cameraDevice.close();             // 2. 关闭相机
            mCameraDevice = null;             // 3. 将正在使用的相机指向null
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            MyLogUtil.e(TAG, "onError");
            // 当相机发生错误时执行以下操作:
            mCameraOpenCloseLock.release();      // 1. 释放访问许可
            cameraDevice.close();                // 2. 关闭相机
            mCameraDevice = null;                // 3, 将正在使用的相机指向null
//            finish();                            // 4. 结束当前Activity
        }
    };

    //ImageReader的回调函数 获得yuv数据
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        private byte[] y;
        private byte[] u;
        private byte[] v;
        private byte[] i420;
        private byte[] yv12;
        private byte[] nv21;
        private byte[] nv12;

        private long lastImageTime = System.currentTimeMillis();
        @Override
        public void onImageAvailable(ImageReader reader) {
            //获取最新的一帧的Image
            Image image = reader.acquireLatestImage();
            if (image.getFormat() == ImageFormat.YUV_420_888) {
//                MyLogUtil.e(TAG, "width: " + image.getWidth());//1920
//                MyLogUtil.e(TAG, "height: " + image.getHeight());//1080
//                MyLogUtil.e(TAG, "format: " + image.getFormat());//ImageFormat.YUV_420_888 =35
//                MyLogUtil.e(TAG, "timeStamp: " +( image.getTimestamp()/1000000 -last));//时间戳
                lastImageTime = image.getTimestamp()/1000000;//33
                if (nv21 == null) {
                    nv21 = new byte[image.getWidth() * image.getHeight() * 3 / 2];
                    nv12 = new byte[image.getWidth() * image.getHeight() * 3 / 2];
                    i420 = new byte[image.getWidth() * image.getHeight() * 3 / 2];
                    yv12 = new byte[image.getWidth() * image.getHeight() * 3 / 2];
                }
                if (y == null) {
                    y = new byte[image.getPlanes()[0].getBuffer().remaining()];
                    u = new byte[image.getPlanes()[1].getBuffer().remaining()];
                    v = new byte[image.getPlanes()[2].getBuffer().remaining()];
                }
                // 从image里获取三个plane
                Image.Plane[] planes = image.getPlanes();
                for (int i = 0; i < planes.length; i++) {
                    ByteBuffer iBuffer = planes[i].getBuffer();
                    int iSize = iBuffer.remaining();
//                    MyLogUtil.e(TAG, "pixelStride  " + planes[i].getPixelStride());
//                    MyLogUtil.e(TAG, "rowStride   " + planes[i].getRowStride());
//                    MyLogUtil.e(TAG, "buffer Size  " + iSize);
//                    MyLogUtil.e(TAG, "Finished reading data from plane  " + i);
                }
//                pixelStride  1
//                rowStride   1920
//                buffer Size  1843200
//                Finished reading data from plane  0
//                pixelStride  2
//                rowStride   1920
//                buffer Size  921599
//                Finished reading data from plane  1
//                pixelStride  2
//                rowStride   1920
//                buffer Size  921599
//                Finished reading data from plane  2
                int width = image.getWidth();
                int height = image.getHeight();
                planes[0].getBuffer().get(y);
                planes[1].getBuffer().get(u);
                planes[2].getBuffer().get(v);

                if (mCameraListener != null) {
                    mCameraListener.onPreview(y, u, v, mPreviewSize, planes[0].getRowStride());
                }
                int pixelStride = planes[1].getPixelStride();
                if (pixelStride == 1) {//420p,i420/yv12
                    if (format == ImageFormat.YV12) {
                        //i420
                        System.arraycopy(y, 0, i420, 0, y.length);
                        System.arraycopy(u, 0, i420, y.length, u.length);
                        System.arraycopy(v, 0, i420, y.length + u.length, v.length);
                        YUVUtil.convertI420ToNV21(i420, nv21, width, height);
                    } else {
                        //yv12
                        System.arraycopy(y, 0, yv12, 0, y.length);
                        System.arraycopy(v, 0, yv12, y.length, v.length);
                        System.arraycopy(u, 0, yv12, y.length + v.length, u.length);
                        YUVUtil.convertYV12ToI420(yv12, i420, width, height);
                        YUVUtil.convertI420ToNV21(i420, nv21, width, height);
                    }

                } else if (pixelStride == 2) {//420sp,nv21/nv12
                    //nv21
                    if (format == ImageFormat.NV21) {
                        System.arraycopy(y, 0, nv21, 0, y.length);
                        System.arraycopy(v, 0, nv21, y.length, v.length);
                    } else {
                        //nv12
                        System.arraycopy(y, 0, nv12, 0, y.length);
                        System.arraycopy(u, 0, nv12, y.length, u.length);
                        YUVUtil.convertNV12ToI420(nv12, i420, width, height);
                        YUVUtil.convertI420ToNV21(i420, nv21, width, height);
                    }
                }
                if (mCameraListener != null) {
                    mCameraListener.onCameraPreview(nv21, mPreviewSize.getWidth(), mPreviewSize.getHeight(), mSensorOrientation);
                }
            }

            image.close(); // 这里一定要close，不然预览会卡死,否则不会收到新的Image回调。
        }
    };

    //一个会话的创建需要比较长的时间，当创建成功后就会执行onConfigured回调
    private CameraCaptureSession.StateCallback previewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // 相机关闭时, 直接返回
            if (null == mCameraDevice) {
                return;
            }

            // 会话可行时, 将构建的会话赋给mCaptureSession
            mCaptureSession = cameraCaptureSession;
            try {
                // 自动对焦
                //在该模式中，AF算法连续地修改镜头位置以尝试提供恒定对焦的图像流。
                //聚焦行为应适合静止图像采集; 通常这意味着尽可能快地聚焦。
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 自动闪光：与ON一样，除了相机设备还控制相机的闪光灯组件，在低光照条件下启动它
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // 设置预览帧率为最高，似乎fpsRanges[fpsRanges.length-1]一般就是手机相机能支持的最大帧率，一般也就是[30,30]
                // 至少在mi 8和华为p30 pro上是这样
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges[fpsRanges.length - 1]);

                // 构建上述的请求
                //(CaptureRequest mPreviewRequest是请求捕获参数的集合，包括连续捕获的频率等)
                mPreviewRequest = mPreviewRequestBuilder.build();
                // 重复进行上面构建的请求, 以便显示预览
                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            MyLogUtil.e(TAG, "createCaptureSession Failed");
        }
    };
    private CameraCaptureSession.StateCallback reRecordStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            // 相机关闭时, 直接返回
            if (null == mCameraDevice) {
                return;
            }

            // 会话可行时, 将构建的会话赋给mCaptureSession
            mCaptureSession = cameraCaptureSession;
            try {
                // 自动对焦
                //在该模式中，AF算法连续地修改镜头位置以尝试提供恒定对焦的图像流。
                //聚焦行为应适合静止图像采集; 通常这意味着尽可能快地聚焦。
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 自动闪光：与ON一样，除了相机设备还控制相机的闪光灯组件，在低光照条件下启动它
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                // 设置预览帧率为最高，似乎fpsRanges[fpsRanges.length-1]一般就是手机相机能支持的最大帧率，一般也就是[30,30]
                // 至少在mi 8和华为p30 pro上是这样
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRanges[fpsRanges.length - 1]);

                // 构建上述的请求
                //(CaptureRequest mPreviewRequest是请求捕获参数的集合，包括连续捕获的频率等)
                mPreviewRequest = mPreviewRequestBuilder.build();
                // 重复进行上面构建的请求, 以便显示预览
                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                        null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            MyLogUtil.e(TAG, "createCaptureSession Failed");
        }
    };

    public void onResume() {
        MyLogUtil.e(TAG, "onResume");
        startBackgroundThread();
        // 当屏幕关闭后重新打开, 若SurfaceTexture已经就绪, 此时onSurfaceTextureAvailable不会被回调, 这种情况下
        // 如果SurfaceTexture已经就绪, 则直接打开相机, 否则等待SurfaceTexture已经就绪的回调
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    public void onPause() {
        MyLogUtil.e(TAG, "onPause: ");
        closeCamera();
        stopBackgroundThread();
    }

    public void onDestroy() {

    }

    //设置相机的输出, 以支持全屏预览
    private void getCamera(int facing) {
        try {
            // 遍历设备的所有摄像头
            if (cameraManager != null) {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                    // 设置默认打开前置摄像头
                    Integer lensFacing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing != null && lensFacing != facing) {
                        continue;
                    }
                    // 相机设备支持的所有输出格式（以及相应格式的大小尺寸）的列表。
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }
                    //此相机设备支持的帧速率范围列表。
                    fpsRanges = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    //输出图像需要旋转的顺时针角度
                    mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    //硬件级别
                    hardwareLevel = cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    //计算出最适合的预览尺寸（实际从相机得到的尺寸）
                    //map.getOutputSizes(SurfaceTexture.class)表示SurfaceTexture支持的尺寸List
                    mPreviewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class));
                    formats = map.getOutputFormats();

                    //预览控件的宽高
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    mCameraId = cameraId;   // 获得当前相机的Id

                    MyLogUtil.e(TAG, "相机信息: 朝向facing = " + facing);//0 前置
                    MyLogUtil.e(TAG, "相机信息: 硬件级别level = " + hardwareLevel);//0
                    MyLogUtil.e(TAG, "相机信息: 支持的格式formats = " + Arrays.toString(formats));// [32=RAW_SENSOR, 256=JPEG, 34=PRIVATE, 35=YUV_420_888]
                    MyLogUtil.e(TAG, "相机信息: 帧速率fpsRanges = " + Arrays.toString(fpsRanges));//[[12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]
                    MyLogUtil.e(TAG, "相机信息: 相机方向mOrientation = " + mSensorOrientation);//270
                    MyLogUtil.e(TAG, "相机信息：预览宽高mPreviewSize = " + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());  // 1280*960
                    MyLogUtil.e(TAG, "相机信息：尺寸列表getOutputSizes = " + Arrays.toString(map.getOutputSizes(SurfaceTexture.class)));  // [4160x3120, 4160x2336, 3264x2448, 3120x3120, 1920x1080, 1920x960, 1440x1080, 1440x720, 1280x960, 1280x720, 960x720, 720x720, 640x480, 320x240, 352x288, 208x144, 176x144]
                    MyLogUtil.e(TAG, "相机信息：mCameraId = " + mCameraId);  // 1
                    MyLogUtil.e(TAG, "相机信息：预览宽高 = " + mPreviewSize.getHeight() + "*" + mPreviewSize.getWidth());  // 960*1280

                    if (mCameraListener != null) {
                        mCameraListener.onCameraOpened(mPreviewSize.getWidth(), mPreviewSize.getHeight(), mSensorOrientation);
                    }
                    return;
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    //计算出最适合全屏预览的尺寸
    private Size getPreviewSize(Size[] choices) {

        // 获取当前的屏幕尺寸, 放到一个点对象里
        Point screenSize = new Point();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);

        int screenWidth = screenSize.y;  // 2160
        int screenHeight = screenSize.x; // 1080
        MyLogUtil.e(TAG, "屏幕宽高 (x,y) x=" + screenSize.x + " y=" + screenSize.y); // x=1080 y=2160

        Size chooseSize = null;
        for (Size option : choices) { //先查找preview中是否存在与surfaceview相同宽高的尺寸
//            MyLogUtil.e(TAG, "width" + size.width + " height" + size.height);
//            MyLogUtil.e(TAG, "surfaceWidth" + surfaceWidth + " surfaceHeight" + surfaceHeight);
            if ((option.getWidth() == screenWidth) && (option.getHeight() == screenHeight)) {
                chooseSize = option;
            }
        }
        if (chooseSize == null) {
            // 得到与传入的宽高比最接近的size
            float reqRatio = ((float) screenWidth) / screenHeight;
//            MyLogUtil.e(TAG, "宽高比 " + reqRatio);
            float curRatio, deltaRatio;
            float deltaRatioMin = Float.MAX_VALUE;
            for (Size option : choices) {
                if (option.getWidth() < 240) continue;//1024表示可接受的最小尺寸，否则图像会很模糊，可以随意修改
                curRatio = ((float) option.getWidth()) / option.getHeight();
                deltaRatio = Math.abs(reqRatio - curRatio);
                if (deltaRatio < deltaRatioMin) {
                    deltaRatioMin = deltaRatio;
                    chooseSize = option;
                }
            }
        }
        if (chooseSize != null) {
            MyLogUtil.e(TAG, "预览尺寸修改为：" + chooseSize.getWidth() + "*" + chooseSize.getHeight());//1920*960
            return chooseSize;
        } else {
            return new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        }

    }

    /**
     * 打开相机
     * 1. 获取相机权限
     * 2. 根据相机特性选取合适的Camera
     * 3. 通过CameraManager打开选择的相机
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(Objects.requireNonNull(mContext), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // 设置相机
        getCamera(facing);

        try {
            // 尝试获得相机开打关闭许可, 等待2500时间仍没有获得则排除异常
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // 打开相机, 参数是: 相机id, 相机状态回调, 子线程处理器
            if (cameraManager != null) {
                cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    //创建预览 + ImageReader
    private void createPreviewAndImageReaderSession() {
        try {
            if(mCaptureSession!= null){
                mCaptureSession.stopRepeating();//停止预览，准备切换到录制视频
                mCaptureSession.close();//关闭预览的会话，需要重新创建录制视频的会话
                mCaptureSession = null;
            }
            if(mImageReader!= null){
                mImageReader.close();//关闭预览的会话，需要重新创建录制视频的会话
                mImageReader = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        try {
            // 输入相机的尺寸必须是相机支持的尺寸，这样画面才能不失真，TextureView输入相机的尺寸也是这个
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), imageFormat, /*maxImages*/1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);   // 设置监听和后台线程处理器
            // 获取用来预览的texture实例
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface

            // 预览请求构建(创建适合相机预览窗口的请求：CameraDevice.TEMPLATE_PREVIEW字段)
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(previewSurface);  //请求捕获的目标surface
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), previewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //预览+录制+ImageReader
    private void createPreviewAndRecordAndImageReaderSession() {
        try {
            if(mCaptureSession!= null){
                mCaptureSession.stopRepeating();//停止预览，准备切换到录制视频
                mCaptureSession.close();//关闭预览的会话，需要重新创建录制视频的会话
                mCaptureSession = null;
            }
            if(mImageReader!= null){
                mImageReader.close();//关闭预览的会话，需要重新创建录制视频的会话
                mImageReader = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // 输入相机的尺寸必须是相机支持的尺寸，这样画面才能不失真，TextureView输入相机的尺寸也是这个
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), imageFormat, /*maxImages*/1);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);// 设置监听和后台线程处理器
        setMediaRecorderConfig(mPreviewSize.getWidth(), mPreviewSize.getHeight(), mSensorOrientation);
        try {
            // 获取用来预览的texture实例
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            Surface recorderSurface = mMediaRecorder.getSurface();//从获取录制视频需要的Surface
            // 预览请求构建(创建适合相机录制视频的请求：CameraDevice.TEMPLATE_RECORD字段)
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.addTarget(previewSurface);  //请求捕获的目标surface
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            mPreviewRequestBuilder.addTarget(recorderSurface);
            // 创建录制的捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface(), recorderSurface), reRecordStateCallback, mBackgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //关闭正在使用的相机
    private void closeCamera() {
        try {
            // 获得相机开打关闭许可
            mCameraOpenCloseLock.acquire();
            // 关闭捕获会话
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            // 关闭当前相机
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            // 关闭拍照处理器
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            // 释放相机开打关闭许可
            mCameraOpenCloseLock.release();
        }
    }

    //开启子线程
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    //停止子线程
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //初始化MediaRecorder
    private void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    public void setMediaRecorderConfig(int width, int height, int sensorOrientation) {
        File file = new File(mContext.getExternalCacheDir(), "demo.mp4");
        if (file.exists()) {
            file.delete();
        }
        MyLogUtil.e(TAG, "视频录制" + file.getPath());
//            mMediaRecorder.setCamera(mCamera);//camera1
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//camera1
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//camera2
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(width, height);
        mMediaRecorder.setOrientationHint(sensorOrientation);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(8 * width * height);
        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void startRecord() {
        MyLogUtil.e(TAG, "startRecord");
        isRecording = true;
        mMediaRecorder.start();
    }

    public void stopRecord() {
        isRecording = false;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        createPreviewAndRecordAndImageReaderSession();
    }


}
