package com.example.tdycamera.mycamera.camera2;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;
import com.example.tdycamera.view.AutoFitTextureView;

import android.media.MediaRecorder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.util.DisplayMetrics;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Helper {
    private static final String TAG = "Camera2BasicFragment";

    // Camera2 API提供的最大预览宽度和高度
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private Context mContext;
    private String mCameraId;                    //正在使用的相机id
    private AutoFitTextureView mTextureView;  // 预览使用的自定义TextureView控件
    private boolean isFrontCamera = true;               //是否前置摄像头
    private int mOrientation;//获取相机角度

    private CameraCaptureSession mCaptureSession;// 预览用的获取会话
    private CameraDevice mCameraDevice;          // 正在使用的相机
    private Size selectPreviewSize;              // 从相机支持的尺寸中选出来的最佳预览尺寸
    private Size mPreviewSize;                   // 预览数据的尺寸
    private static Range<Integer>[] fpsRanges;   // 相机的FPS范围

    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器
    private ImageReader mImageReader;            // 用于获取画面的数据，并进行识别 YUV_420_888
    private int imageFormat = ImageFormat.YUV_420_888;

    private CaptureRequest.Builder mPreviewRequestBuilder;  // 预览请求构建器
    private CaptureRequest mPreviewRequest;      // 预览请求, 由上面的构建器构建出来
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);  // 信号量控制器

    private CameraListener mCameraListener;
    private MediaRecorder mMediaRecorder;

    public Camera2Helper(Context context, AutoFitTextureView mTextureView, CameraListener cameraListener) {
        this.mContext = context;
        this.mTextureView = mTextureView;
        this.mCameraListener = cameraListener;
        initMediaRecorder();
    }

    /**
     * SurfaceTexture监听器
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);//1080 2160
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

    /**
     * 相机状态改变的回调函数
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // 当相机打开执行以下操作:
            mCameraOpenCloseLock.release();  // 1. 释放访问许可
            mCameraDevice = cameraDevice;   // 2. 将正在使用的相机指向将打开的相机
            createCameraPreviewSession();   // 3. 创建相机预览会话
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // 当相机失去连接时执行以下操作:
            mCameraOpenCloseLock.release();   // 1. 释放访问许可
            cameraDevice.close();             // 2. 关闭相机
            mCameraDevice = null;             // 3. 将正在使用的相机指向null
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            // 当相机发生错误时执行以下操作:
            mCameraOpenCloseLock.release();      // 1. 释放访问许可
            cameraDevice.close();                // 2. 关闭相机
            mCameraDevice = null;                // 3, 将正在使用的相机指向null
//            finish();                            // 4. 结束当前Activity
        }
    };

    /**
     * ImageReader的回调函数
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        private byte[] y;
        private byte[] u;
        private byte[] v;

        @Override
        public void onImageAvailable(ImageReader reader) {
            //我们可以将这帧数据转成字节数组，类似于Camera1的PreviewCallback回调的预览帧数据
            Image image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            if (mCameraListener != null) {
                mCameraListener.onPreviewFrame(image, image.getWidth(), image.getHeight(), mOrientation);
            }
            image.close(); // 这里一定要close，不然预览会卡死
        }
    };

    public boolean isFrontCamera() {
        return isFrontCamera;
    }

    public void onResume() {
        MyLogUtil.e(TAG, "onResume: ");
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
        // 设置相机输出
        setUpCameraOutputs();

        // 获取CameraManager的实例
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            // 尝试获得相机开打关闭许可, 等待2500时间仍没有获得则排除异常
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // 打开相机, 参数是: 相机id, 相机状态回调, 子线程处理器
            assert manager != null;
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * 设置相机的输出, 以支持全屏预览
     * <p>
     * 处理流程如下:
     * 1. 获取当前的摄像头支持的输出map和帧率信息，并跳过前置摄像头
     * 2. 判断显示方向和摄像头传感器方向是否一致, 是否需要旋转画面
     * 3. 获取手机屏幕尺寸和相机的输出尺寸, 选择最合适的全屏预览尺寸
     * 4. 设置用于显示的TextureView和SurfaceView的尺寸，新建ImageReader对象
     */
    private void setUpCameraOutputs() {
        // 获取CameraManager实例
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            // 遍历运行本应用的设备的所有摄像头
            assert manager != null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // 如果该摄像头是前置摄像头, 则看下一个摄像头(本应用不使用前置摄像头)
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }

                // StreamConfigurationMap包含相机的可输出尺寸信息
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // 得到相机的帧率范围，可以在构建CaptureRequest的时候设置画面的帧率
                fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                MyLogUtil.e(TAG, "setUpCameraOutputs: fpsRanges = " + Arrays.toString(fpsRanges));

                // 获取手机目前的旋转方向(横屏还是竖屏, 对于"自然"状态下高度大于宽度的设备来说横屏是ROTATION_90
                // 或者ROTATION_270,竖屏是ROTATION_0或者ROTATION_180)
                int displayRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                MyLogUtil.e(TAG, "displayRotation: " + displayRotation);   // displayRotation: 0
                MyLogUtil.e(TAG, "sensorOritentation: " + sensorOrientation);  // sensorOritentation: 270
                switch (displayRotation) {
                    // ROTATION_0和ROTATION_180都是竖屏只需做同样的处理操作
                    // 显示为竖屏时, 若传感器方向为90或者270, 则需要进行转换(标志位置true)
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270) {
                            MyLogUtil.e(TAG, "swappedDimensions set true !");//true
                            swappedDimensions = true;
                        }
                        break;
                    // ROTATION_90和ROTATION_270都是横屏只需做同样的处理操作
                    // 显示为横屏时, 若传感器方向为0或者180, 则需要进行转换(标志位置true)
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        MyLogUtil.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                // 获取当前的屏幕尺寸, 放到一个点对象里
                Point screenSize = new Point();
                DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
                ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);
                // 初始时将屏幕认为是横屏的
                int screenWidth = screenSize.y;  // 2160
                int screenHeight = screenSize.x; // 1080
                MyLogUtil.e(TAG, "screenWidth = " + screenWidth + ", screenHeight = " + screenHeight); // 2160 1080

                // swappedDimensions: (竖屏时true，横屏时false)
                if (swappedDimensions) {
                    screenWidth = screenSize.x;  // 1080
                    screenHeight = screenSize.y; // 2029
                }
                // 尺寸太大时的极端处理
                if (screenWidth > MAX_PREVIEW_HEIGHT) screenWidth = MAX_PREVIEW_HEIGHT;
                if (screenHeight > MAX_PREVIEW_WIDTH) screenHeight = MAX_PREVIEW_WIDTH;

                MyLogUtil.e(TAG, "after adjust, screenWidth = " + screenWidth + ", screenHeight = " + screenHeight); // 1080 1920

                // 自动计算出最适合的预览尺寸（实际从相机得到的尺寸，也是ImageReader的输入尺寸）
                // 第一个参数:map.getOutputSizes(SurfaceTexture.class)表示SurfaceTexture支持的尺寸List
                selectPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        screenWidth, screenHeight, swappedDimensions);

                // 这里返回的selectPreviewSize，要注意横竖屏的区分
                MyLogUtil.e(TAG, "selectPreviewSize.getWidth: " + selectPreviewSize.getWidth());  // 1920
                MyLogUtil.e(TAG, "selectPreviewSize.getHeight: " + selectPreviewSize.getHeight());  // 1080

                // 横竖屏尺寸交换，以便后面设置各种surface统一代码
                if (swappedDimensions) mPreviewSize = selectPreviewSize;
                else {
                    mPreviewSize = new Size(selectPreviewSize.getHeight(), selectPreviewSize.getWidth());
                }
                mOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (mCameraListener != null) {
                    mCameraListener.onCameraOpened(mPreviewSize.getWidth(), mPreviewSize.getHeight(), mOrientation);
                }
                MyLogUtil.e(TAG, "mPreviewSize.getWidth: " + mPreviewSize.getWidth());  // 1920
                MyLogUtil.e(TAG, "mPreviewSize.getHeight: " + mPreviewSize.getHeight());  // 1080

                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());

                mCameraId = cameraId;   // 获得当前相机的Id
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /**
     * 计算出最适合全屏预览的尺寸
     * 原则是宽度和屏幕宽度相等，高度最接近屏幕高度
     *
     * @param choices      相机支持的尺寸list
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 最合适的预览尺寸
     */
    private static Size chooseOptimalSize(Size[] choices, int screenWidth, int screenHeight, boolean swappedDimensions) {
        List<Size> bigEnough = new ArrayList<>();
        StringBuilder stringBuilder = new StringBuilder();
        if (swappedDimensions) {  // 竖屏
            for (Size option : choices) {
                String str = "[" + option.getWidth() + ", " + option.getHeight() + "]";
                stringBuilder.append(str);
                if (option.getHeight() != screenWidth || option.getWidth() > screenHeight)
                    continue;
                bigEnough.add(option);
            }
        } else {     // 横屏
            for (Size option : choices) {
                String str = "[" + option.getWidth() + ", " + option.getHeight() + "]";
                stringBuilder.append(str);
                if (option.getWidth() != screenHeight || option.getHeight() > screenWidth)
                    continue;
                bigEnough.add(option);
            }
        }
        MyLogUtil.e(TAG, "chooseOptimalSize: " + stringBuilder);

        if (bigEnough.size() > 0) {
            return Collections.max(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[choices.length / 2];
        }
    }

    /**
     * 创建预览对话
     * <p>
     * 1. 获取用于输出预览的surface
     * 2. CaptureRequestBuilder的基本配置
     * 3. 创建CaptureSession，等待回调
     * 4. 会话创建完成后，配置CaptureRequest为自动聚焦模式，并设为最高帧率输出
     * 5. 重复构建上述请求，以达到实时预览
     */
    private void createCameraPreviewSession() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        try {
            // 输入相机的尺寸必须是相机支持的尺寸，这样画面才能不失真，TextureView输入相机的尺寸也是这个
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    imageFormat, /*maxImages*/1);
            mImageReader.setOnImageAvailableListener(   // 设置监听和后台线程处理器
                    mOnImageAvailableListener, mBackgroundHandler);
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
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        //一个会话的创建需要比较长的时间，当创建成功后就会执行onConfigured回调
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
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            MyLogUtil.e(TAG, "createCaptureSession Failed");

                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void config() {
        try {
            mCaptureSession.stopRepeating();//停止预览，准备切换到录制视频
            mCaptureSession.close();//关闭预览的会话，需要重新创建录制视频的会话
            mCaptureSession = null;
            mImageReader.close();
            mImageReader = null;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // 输入相机的尺寸必须是相机支持的尺寸，这样画面才能不失真，TextureView输入相机的尺寸也是这个
        mImageReader = ImageReader.newInstance(selectPreviewSize.getWidth(), selectPreviewSize.getHeight(),
                imageFormat, /*maxImages*/1);
        mImageReader.setOnImageAvailableListener(   // 设置监听和后台线程处理器
                mOnImageAvailableListener, mBackgroundHandler);
        setMediaRecorderConfig(selectPreviewSize.getWidth(), selectPreviewSize.getHeight());
        try {
            // 获取用来预览的texture实例
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(selectPreviewSize.getWidth(), selectPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            Surface recorderSurface = mMediaRecorder.getSurface();//从获取录制视频需要的Surface
            // 预览请求构建(创建适合相机预览窗口的请求：CameraDevice.TEMPLATE_PREVIEW字段)
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewRequestBuilder.addTarget(previewSurface);  //请求捕获的目标surface
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());
            mPreviewRequestBuilder.addTarget(recorderSurface);
            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface(), recorderSurface),
//            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,recorderSurface),
                    new CameraCaptureSession.StateCallback() {
                        //一个会话的创建需要比较长的时间，当创建成功后就会执行onConfigured回调
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
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            MyLogUtil.e(TAG, "createCaptureSession Failed");

                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭正在使用的相机
     */
    private void closeCamera() {
        try {
            // 获得相机开打关闭许可
            mCameraOpenCloseLock.acquire();
            // 关闭捕获会话
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            // 关闭当前相机
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            // 关闭拍照处理器
            if (null != mImageReader) {
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

    /**
     * 开启子线程
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * 停止子线程
     */
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


    /**
     * 初始化MediaRecorder
     */
    private void initMediaRecorder() {
        mMediaRecorder = new MediaRecorder();
    }

    public void setMediaRecorderConfig(int width, int height) {
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
        mMediaRecorder.setOrientationHint(270);
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

    public void startRecord() {
        config();
        MyLogUtil.e(TAG, "startRecord");
        mMediaRecorder.start();
    }

    public void stopRecord() {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        createCameraPreviewSession();
    }

    /**
     * 比较两个Size的大小（基于它们的area）
     */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
