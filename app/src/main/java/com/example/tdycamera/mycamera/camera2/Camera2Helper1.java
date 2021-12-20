package com.example.tdycamera.mycamera.camera2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraOfflineSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;
import com.example.tdycamera.view.AutoFitTextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.M)
public class Camera2Helper1 {
    private String TAG = "Camera2Helper1";
    private Context mContext;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;//单个相机实例
    private CameraCaptureSession mCameraCaptureSession;//捕获会话
    private ImageReader pictureImageReader;//图片
    private ImageReader yuvImageReader;//yuv视频帧数据
    private Executor executor = Executors.newSingleThreadExecutor();
    private CameraCharacteristics mCameraCharacteristics;

    private AutoFitTextureView autoFitTextureView;  // 预览使用的自定义TextureView控件
    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程，不阻塞UI的附加线程。
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器，在后台运行任务
    private Size mPreviewSize;//预览尺寸
    private Size mVideoSize;//视频尺寸
    private Size mPictureSize;//照片尺寸
    // Camera2 API提供的最大预览宽度和高度
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    private Range<Integer>[] mFpsRanges;   // 相机的FPS范围
    private int mSensorOrientation;//相机传感器的方向
    private String mCameraId;                    //正在使用的相机id
    private int mFacing = CameraCharacteristics.LENS_FACING_FRONT;//朝向
    private int[] mFormats;//设备支持的格式
    private int hardwareLevel;//硬件级别
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);  // 信号量控制器

    private CameraListener mCameraListener;

    public Camera2Helper1(Context context, AutoFitTextureView mTextureView, CameraListener cameraListener) {
        this.mContext = context;
        this.autoFitTextureView = mTextureView;
        this.mCameraListener = cameraListener;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }


    //捕捉请求发送给相机设备进度的变化
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }
    };

    //当相机捕捉事务的状态发生变化时
    private CameraCaptureSession.StateCallback mCameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCameraDevice == null) {//相机已经关闭
                return;
            }
            mCameraCaptureSession = session;//获得CameraCaptureSession实例
            setRepeatingRequest();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

    //拍照生成的帧
    private ImageReader.OnImageAvailableListener pictureImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            //获取最新的一帧的Image
            Image image = imageReader.acquireLatestImage();
            //因为是ImageFormat.JPEG格式，所以 image.getPlanes()返回的数组只有一个，也就是第0个。
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            //ImageFormat.JPEG格式直接转化为Bitmap格式。
            Bitmap temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            //因为摄像机数据默认是横的，所以需要旋转90度。

            //抛出去展示或存储。

            //一定需要close，否则不会收到新的Image回调。
            image.close();
        }
    };

    //生成yuv数据
    private ImageReader.OnImageAvailableListener yuvImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            //获取最新的一帧的Image
            Image image = imageReader.acquireLatestImage();

            //一定需要close，否则不会收到新的Image回调。
            image.close();
        }
    };

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            mCameraDevice = null;
        }
    };

    private CameraManager.AvailabilityCallback mAvailabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            super.onCameraAvailable(cameraId);
        }
    };

    private CameraManager.TorchCallback mTorchCallback = new CameraManager.TorchCallback() {
        @Override
        public void onTorchModeUnavailable(@NonNull String cameraId) {
            super.onTorchModeUnavailable(cameraId);
        }
    };

    /*********************************************************************************************/
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
    //拍照
    private void takePicture() {
        stopRepeating();
        abortCaptures();
        capture();
    }

    //计算出最适合全屏预览的尺寸
    private Size getPreviewSize(Size[] choices) {

        // 获取当前的屏幕尺寸, 放到一个点对象里
        Point screenSize = new Point();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);
        int screenWidth = screenSize.x;  // 1080
        int screenHeight = screenSize.y; // 2160

        int displayRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }
        if (swappedDimensions) {
            screenWidth = screenSize.y;
            screenHeight = screenSize.x;
        }

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

    //设置相机的输出, 以支持全屏预览
    private void getCamera(int facing) {
        // 遍历设备的所有摄像头
        if (mCameraManager != null) {
            for (String cameraId : getCameraIdList()) {
                mCameraCharacteristics = getCameraCharacteristics(cameraId);
                // 设置默认打开前置摄像头
                Integer lensFacing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (lensFacing != null && lensFacing != facing) {
                    continue;
                }
                // 相机设备支持的所有输出格式（以及相应格式的大小尺寸）的列表。
                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                //此相机设备支持的帧速率范围列表。
                mFpsRanges = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                //输出图像需要旋转的顺时针角度
                mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                //硬件级别
                hardwareLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                //计算出最适合的预览尺寸（实际从相机得到的尺寸）
                //map.getOutputSizes(SurfaceTexture.class)表示SurfaceTexture支持的尺寸List
                mPreviewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class));
                mVideoSize = getPreviewSize(map.getOutputSizes(MediaRecorder.class));
                mPictureSize = getPreviewSize(map.getOutputSizes(ImageReader.class));
                mFormats = map.getOutputFormats();

                //预览控件的宽高
                int orientation = mContext.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    autoFitTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    autoFitTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                mCameraId = cameraId;   // 获得当前相机的Id

                MyLogUtil.e(TAG, "相机信息: 朝向facing = " + facing);//0 前置
                MyLogUtil.e(TAG, "相机信息: 硬件级别level = " + hardwareLevel);//0
                MyLogUtil.e(TAG, "相机信息: 支持的格式formats = " + Arrays.toString(mFormats));// [32=RAW_SENSOR, 256=JPEG, 34=PRIVATE, 35=YUV_420_888]
                MyLogUtil.e(TAG, "相机信息: 帧速率fpsRanges = " + Arrays.toString(mFpsRanges));//[[12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]
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
    }


    /*********************************************************************************************/
    //提交一个获取单张图片的捕捉请求，常用于单张拍照
    private void capture() {
        MyLogUtil.e(TAG, "capture");
        try {
            //捕获单个图片的参数设置
            CaptureRequest.Builder captureBuilder = createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            pictureImageReader = ImageReader.newInstance(mPictureSize.getWidth(), mPictureSize.getHeight(), ImageFormat.JPEG, 2);
            pictureImageReader.setOnImageAvailableListener(pictureImageAvailableListener, mBackgroundHandler);
            //生成的新帧
            captureBuilder.addTarget(pictureImageReader.getSurface());
            // Orientation 方向
            int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
            MyLogUtil.e(TAG, "设置图片的方向=" + rotation);

            mCameraCaptureSession.capture(captureBuilder.build(), captureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //类似capture，只是参数为executor
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void captureSingleRequest(CaptureRequest captureRequest) {
        MyLogUtil.e(TAG, "capture");
        try {
            mCameraCaptureSession.captureSingleRequest(captureRequest, executor, captureCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //类似于capture，但是这一组CaptureRequest中间不能被其他CaptureRequest插入进来.此方法可保证不会在突发中穿插其他请求
    private void captureBurst(List<CaptureRequest> captureRequestList) {
        try {
            mCameraCaptureSession.captureBurst(captureRequestList, captureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //类似于captureBurst，只是参数为executor
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void captureBurstRequests(List<CaptureRequest> captureRequestList) {
        try {
            mCameraCaptureSession.captureBurstRequests(captureRequestList, executor, captureCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //重复请求获取图像数据，常用于预览或连拍
    private void setRepeatingRequest() {
        try {
            SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            yuvImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            yuvImageReader.setOnImageAvailableListener(yuvImageAvailableListener, mBackgroundHandler);
            //预览参数设置
            CaptureRequest.Builder previewBuilder = createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(yuvImageReader.getSurface());
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            mCameraCaptureSession.setRepeatingRequest(previewBuilder.build(), captureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //类似于setRepeatingRequest，只是参数为executor
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void setSingleRepeatingRequest(CaptureRequest captureRequest) {
        try {
            mCameraCaptureSession.setSingleRepeatingRequest(captureRequest, executor, captureCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //重复捕获一系列图像
    private void setRepeatingBurst(List<CaptureRequest> captureRequestList) {
        try {
            mCameraCaptureSession.setRepeatingBurst(captureRequestList, captureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //类似于setRepeatingBurst，只是参数为executor
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void setRepeatingBurstRequests(List<CaptureRequest> captureRequestList) {
        try {
            mCameraCaptureSession.setRepeatingBurstRequests(captureRequestList, executor, captureCallback);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void prepare(Surface surface) {
        try {
            mCameraCaptureSession.prepare(surface);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //尽可能快的取消当前队列中或正在处理中的所有捕捉请求。
    private void abortCaptures() {
        try {
            mCameraCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //停止任何一个正常进行的重复请求
    private void stopRepeating() {
        try {
            mCameraCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //异步关闭这个 CameraCaptureSession。应当在相机释放的步骤中对 session 也进行关闭操作。
    private void closeCameraCaptureSession() {
        mCameraCaptureSession.close();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void finalizeOutputConfigurations(List<OutputConfiguration> outputConfigurationList) {
        try {
            mCameraCaptureSession.finalizeOutputConfigurations(outputConfigurationList);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void updateOutputConfiguration(OutputConfiguration outputConfiguration) {
        try {
            mCameraCaptureSession.updateOutputConfiguration(outputConfiguration);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //获取该 CameraCaptureSession 创建对应的 CameraDevice 对象。
    private CameraDevice getDevice() {
        return mCameraCaptureSession.getDevice();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public Surface getInputSurface() {
        return mCameraCaptureSession.getInputSurface();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean isReprocessable() {
        return mCameraCaptureSession.isReprocessable();
    }

    //是否支持离线
    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean supportsOfflineProcessing(Surface surface) {
        return mCameraCaptureSession.supportsOfflineProcessing(surface);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private CameraOfflineSession switchToOffline(Collection<Surface> offlineSurfaces) {
        try {
            return mCameraCaptureSession.switchToOffline(offlineSurfaces, executor, new CameraOfflineSession.CameraOfflineSessionCallback() {
                @Override
                public void onReady(@NonNull CameraOfflineSession cameraOfflineSession) {

                }

                @Override
                public void onSwitchFailed(@NonNull CameraOfflineSession cameraOfflineSession) {

                }

                @Override
                public void onIdle(@NonNull CameraOfflineSession cameraOfflineSession) {

                }

                @Override
                public void onError(@NonNull CameraOfflineSession cameraOfflineSession, int i) {

                }

                @Override
                public void onClosed(@NonNull CameraOfflineSession cameraOfflineSession) {

                }
            });
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /*********************************************************************************************/
    //与相机的连接
    private void closeCameraDevice() {
        mCameraDevice.close();
    }

    //创建捕获请求
    private CaptureRequest.Builder createCaptureRequest(int template) {
        try {
            return mCameraDevice.createCaptureRequest(template);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    //创建捕获会话(旧)
    private void createCaptureSession() {
        try {
            SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            yuvImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            yuvImageReader.setOnImageAvailableListener(yuvImageAvailableListener, mBackgroundHandler);
            //预览参数设置
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(previewSurface);
            surfaceList.add(yuvImageReader.getSurface());
            mCameraDevice.createCaptureSession(surfaceList, mCameraCaptureSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //创建捕获会话(新）
    @RequiresApi(api = Build.VERSION_CODES.P)
    private void createCaptureSession(SessionConfiguration sessionConfiguration) {
        try {
            mCameraDevice.createCaptureSession(sessionConfiguration);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //提供 Surface 的目标输出集来创建捕获会话
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void createCaptureSessionByOutputConfigurations(List<OutputConfiguration> outputConfigurationList) {
        try {
            mCameraDevice.createCaptureSessionByOutputConfigurations(outputConfigurationList, mCameraCaptureSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //创建受约束的高速捕获会话。
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void createConstrainedHighSpeedCaptureSession(List<Surface> surfaceList) {
        try {
            mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaceList, mCameraCaptureSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //创建重新处理捕获请求
    @RequiresApi(api = Build.VERSION_CODES.M)
    private CaptureRequest.Builder createReprocessCaptureRequest(TotalCaptureResult totalCaptureResult) {
        try {
            return mCameraDevice.createReprocessCaptureRequest(totalCaptureResult);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    //通过向相机设备提供所需的重新处理输入表面配置和表面的目标输出集，创建新的可重新处理相机捕获会话。
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void createReprocessableCaptureSession(InputConfiguration inputConfiguration, List<Surface> surfaceList) {
        try {
            mCameraDevice.createReprocessableCaptureSession(inputConfiguration, surfaceList, mCameraCaptureSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //通过向`OutputConfiguration`相机设备提供所需的重新处理输入配置和输出，创建新的可重新处理相机捕获会话。
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void createReprocessableCaptureSessionByConfigurations(InputConfiguration inputConfiguration, List<OutputConfiguration> outputConfigurationList) {
        try {
            mCameraDevice.createReprocessableCaptureSessionByConfigurations(inputConfiguration, outputConfigurationList, mCameraCaptureSessionStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //获取相机音频限制
    @RequiresApi(api = Build.VERSION_CODES.R)
    private int getCameraAudioRestriction() {
        try {
            return mCameraDevice.getCameraAudioRestriction();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return -1;
    }

    //获取ID
    private String getId() {
        return mCameraDevice.getId();
    }

    //检查特定SessionConfiguration相机设备是否支持。
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private boolean isSessionConfigurationSupported(SessionConfiguration sessionConfiguration) {
        try {
            return mCameraDevice.isSessionConfigurationSupported(sessionConfiguration);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    //设置相机音频限制
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void setCameraAudioRestriction(int mode) {
        try {
            mCameraDevice.setCameraAudioRestriction(mode);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*********************************************************************************************/

    //查询摄像头设备的能力
    private CameraCharacteristics getCameraCharacteristics(String mCameraId) {
        try {
            return mCameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    //获取相机 ID 列表
    private String[] getCameraIdList() {
        try {
            return mCameraManager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
    //打开相机
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(Objects.requireNonNull(mContext), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // 设置相机
        getCamera(mFacing);

        try {
            // 尝试获得相机开打关闭许可, 等待2500时间仍没有获得则排除异常
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // 打开相机, 参数是: 相机id, 相机状态回调, 子线程处理器
            if (mCameraManager != null) {
                mCameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    //获取ConcurrentCameraIds
    @RequiresApi(api = Build.VERSION_CODES.R)
    private Set<Set<String>> getConcurrentCameraIds() {
        try {
            return mCameraManager.getConcurrentCameraIds();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    //检查是否`SessionConfiguration`可以同时配置提供的一组相机设备及其对应的设备。
    @RequiresApi(api = Build.VERSION_CODES.R)
    private boolean isConcurrentSessionConfigurationSupported(Map<String, SessionConfiguration> cameraIdAndSessionConfig) {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            return mCameraManager.isConcurrentSessionConfigurationSupported(cameraIdAndSessionConfig);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return false;
    }


    private void registerAvailabilityCallback() {
        mCameraManager.registerAvailabilityCallback(mAvailabilityCallback, mBackgroundHandler);
    }

    private void unregisterAvailabilityCallback() {
        mCameraManager.unregisterAvailabilityCallback(mAvailabilityCallback);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void registerTorchCallback() {
        mCameraManager.registerTorchCallback(mTorchCallback, mBackgroundHandler);
    }

    private void unregisterTorchCallback() {
        mCameraManager.unregisterTorchCallback(mTorchCallback);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setTorchMode(String cameraId, boolean enabled) {
        try {
            mCameraManager.setTorchMode(cameraId, enabled);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
