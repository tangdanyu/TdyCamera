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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Helper {
    private static final String TAG = "Camera2Helper";
    private Context mContext;
    private CameraManager mCameraManager;                   //相机管理类
    private CameraDevice mCameraDevice;                     //正在使用的相机实例
    private CameraCharacteristics mCameraCharacteristics;   //相机特性
    private CameraCaptureSession mCameraCaptureSession;     //捕获会话
    private CaptureRequest.Builder mCaptureRequestBuilder;  //捕获会话构建器
    private ImageReader pictureImageReader;                 //图片
    private ImageReader yuvImageReader;                     //yuv视频帧数据

    private AutoFitTextureView autoFitTextureView;          // 预览使用的自定义TextureView控件
    private int facing = CameraCharacteristics.LENS_FACING_FRONT;//朝向
    private boolean isFrontCamera = true;                   //是否前置摄像头
    private String mCameraId;                               //正在使用的相机id
    private Size mPreviewSize;                              //预览数据的尺寸
    private Size mVideoSize;                                //录制视频尺寸 尺寸不要大于1080p，因为MediaRecorder无法处理如此高分辨率的视频。
    private Size mPictureSize;                              //照片尺寸
    private int[] formats;                                  //设备支持的格式
    private int hardwareLevel;                              //硬件级别
    private Range<Integer>[] fpsRanges;                     //相机的FPS范围
    private int mSensorOrientation;                         //相机传感器的方向
    private int format = ImageFormat.NV21;                  //设置YUV420

    private MediaRecorder mMediaRecorder;                   // 尺寸不要大于1080p，因为MediaRecorder无法处理如此高分辨率的视频。
    private boolean isRecordVideo = true;          //是否录制视频
    private boolean isRecording = false;                    //是否正在录制
    private String mNextVideoAbsolutePath;                  //录制视频路径
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }


    private Semaphore mCameraOpenCloseLock = new Semaphore(1);  // 信号量控制器
    private HandlerThread mBackgroundThread;     // 处理拍照等工作的子线程，不阻塞UI的附加线程。
    private Handler mBackgroundHandler;          // 上面定义的子线程的处理器，在后台运行任务
    // Camera2 API提供的最大预览宽度和高度
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private CameraListener mCameraListener;             //相机事件回调
    private byte[] y, u, v, i420, yv12, nv21, nv12;

    public Camera2Helper(Context context, AutoFitTextureView mTextureView, CameraListener cameraListener) {
        this.mContext = context;
        this.autoFitTextureView = mTextureView;
        this.mCameraListener = cameraListener;
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
    }

    //SurfaceTexture与相机关联
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureAvailable: width=" + width + ", height=" + height);// width=1080, height=2160
            openCamera();    // SurfaceTexture就绪后回调执行打开相机操作
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            MyLogUtil.e(TAG, "onSurfaceTextureSizeChanged: width=" + width + ", height=" + height);// width=1080, height=2160
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };
    //捕捉请求发送给相机设备进度的变化
    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            MyLogUtil.e(TAG, "onCaptureCompleted");

            try {
                if (null == mCameraDevice) {
                    return;
                }
                CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(pictureImageReader.getSurface());

                // Use the same AE and AF modes as the preview.
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                setAutoFlash(captureBuilder);

                // Orientation
                int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

                CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                   @NonNull CaptureRequest request,
                                                   @NonNull TotalCaptureResult result) {
                        createPreviewAndImageReaderSession();
                    }
                };

                mCameraCaptureSession.stopRepeating();
                mCameraCaptureSession.abortCaptures();
                mCameraCaptureSession.capture(mCaptureRequestBuilder.build(), CaptureCallback, mBackgroundHandler);


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            MyLogUtil.e(TAG, "onCaptureFailed");

        }
    };
    //相机状态改变的回调函数
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            MyLogUtil.e(TAG, "mStateCallback onOpened");
            // 当相机打开执行以下操作:
            mCameraOpenCloseLock.release();  // 1. 释放访问许可
            mCameraDevice = cameraDevice;   // 2. 将正在使用的相机指向将打开的相机
            createPreviewAndImageReaderSession();// 3. 创建相机预览会话
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
    private ImageReader.OnImageAvailableListener yuvImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        private long lastImageTime = System.currentTimeMillis();

        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {

                }
            });
            //获取最新的一帧的Image
            Image image = reader.acquireLatestImage();
            if (image.getFormat() == ImageFormat.YUV_420_888) {
//                MyLogUtil.e(TAG, "width: " + image.getWidth());//1920
//                MyLogUtil.e(TAG, "height: " + image.getHeight());//1080
//                MyLogUtil.e(TAG, "format: " + image.getFormat());//ImageFormat.YUV_420_888 =35
//                MyLogUtil.e(TAG, "timeStamp: " +( image.getTimestamp()/1000000 -last));//时间戳
                lastImageTime = image.getTimestamp() / 1000000;//33
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
    //拍照生成的帧
    private ImageReader.OnImageAvailableListener pictureImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            MyLogUtil.e(TAG, "拍照了");
            //获取最新的一帧的Image
            Image image = imageReader.acquireNextImage();
            if (image != null) {
                //因为是ImageFormat.JPEG格式，所以 image.getPlanes()返回的数组只有一个，也就是第0个。
                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                //ImageFormat.JPEG格式直接转化为Bitmap格式。
                Bitmap temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                //因为摄像机数据默认是横的，所以需要旋转90度。
                if (mCameraListener != null) {
                    mCameraListener.onPictureTaken(bytes);
                }
                //抛出去展示或存储。
            }
            //一定需要close，否则不会收到新的Image回调。
            image.close();
        }
    };
    //一个会话的创建需要比较长的时间，当创建成功后就会执行onConfigured回调
    private CameraCaptureSession.StateCallback previewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if (null == mCameraDevice) {   // 相机关闭时, 直接返回
                return;
            }
            MyLogUtil.e(TAG, "previewStateCallback onConfigured");
            mCameraCaptureSession = cameraCaptureSession;//获得CameraCaptureSession实例
            setRepeatingRequest();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            MyLogUtil.e(TAG, "onConfigureFailed");
        }
    };

    private CameraCaptureSession.StateCallback pictureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if (null == mCameraDevice) {   // 相机关闭时, 直接返回
                return;
            }
            MyLogUtil.e(TAG, "pictureStateCallback onConfigured");
            mCameraCaptureSession = cameraCaptureSession;//获得CameraCaptureSession实例
            try {
                CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                Surface pictureSurface = pictureImageReader.getSurface();
                captureBuilder.addTarget(pictureSurface);
                // Use the same AE and AF modes as the preview.
                captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                setAutoFlash(captureBuilder);
                mCameraCaptureSession.setRepeatingRequest(captureBuilder.build(),
                        mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            MyLogUtil.e(TAG, "onConfigureFailed");
        }
    };

    private CameraCaptureSession.StateCallback reRecordStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if (null == mCameraDevice) {
                return;
            }
            MyLogUtil.e(TAG, "reRecordStateCallback onConfigured");
            mCameraCaptureSession = cameraCaptureSession;
            setRepeatingRequest();
            isRecording = true;
            mMediaRecorder.start();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            MyLogUtil.e(TAG, "onConfigureFailed");
        }
    };

    //重复请求获取图像数据，常用于预览或连拍
    private void setRepeatingRequest() {
        MyLogUtil.e(TAG, "setRepeatingRequest");
        try {
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //清除上个模板的会话
    private void closeCurrentSession() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }

    //创建预览 + ImageReader
    private void createPreviewAndImageReaderSession() {
        MyLogUtil.e(TAG, "创建预览 + ImageReader");
        if (null == mCameraDevice || !autoFitTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            //重置会话
            closeCurrentSession();
            //获取用来预览的texture实例
            SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            //获得yuv格式视频帧
            yuvImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, /*maxImages*/1);
            yuvImageReader.setOnImageAvailableListener(yuvImageAvailableListener, mBackgroundHandler);

            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(previewSurface);
            surfaceList.add(yuvImageReader.getSurface());
            //创建预览模式捕获请求CameraDevice.TEMPLATE_PREVIEW
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(yuvImageReader.getSurface());

            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(surfaceList, previewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPreviewAndRecordAndImageReaderSession() {
        MyLogUtil.e(TAG, "创建预览 + ImageReader + 录像");
        if (null == mCameraDevice || !autoFitTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            //重置会话
            closeCurrentSession();
            setMediaRecorderConfig();
            //获取用来预览的texture实例
            SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            //获得yuv格式视频帧
            yuvImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, /*maxImages*/1);
            yuvImageReader.setOnImageAvailableListener(yuvImageAvailableListener, mBackgroundHandler);

            Surface yuvSurface = yuvImageReader.getSurface();
            Surface recorderSurface = mMediaRecorder.getSurface();
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(previewSurface);
            surfaceList.add(yuvSurface);
            surfaceList.add(recorderSurface);
            //创建录制视频模式捕获请求CameraDevice.TEMPLATE_RECORD
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(yuvSurface);
            mCaptureRequestBuilder.addTarget(recorderSurface);

            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(surfaceList, reRecordStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createPreviewAndPictureAndImageReaderSession() {
        MyLogUtil.e(TAG, "创建预览 + ImageReader + 拍照");
        if (null == mCameraDevice || !autoFitTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            //重置会话
            closeCurrentSession();
            //获取用来预览的texture实例
            SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());  // 设置宽度和高度
            Surface previewSurface = new Surface(texture);  // 用获取输出surface
            //获得yuv格式视频帧
            yuvImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, /*maxImages*/1);
            yuvImageReader.setOnImageAvailableListener(yuvImageAvailableListener, mBackgroundHandler);
            //获得jpeg格式视频帧
            pictureImageReader = ImageReader.newInstance(mPictureSize.getWidth(), mPictureSize.getHeight(), ImageFormat.JPEG, 2);
            pictureImageReader.setOnImageAvailableListener(pictureImageAvailableListener, mBackgroundHandler);
            Surface yuvSurface = yuvImageReader.getSurface();
            Surface pictureSurface = pictureImageReader.getSurface();
            List<Surface> surfaceList = new ArrayList<>();
            surfaceList.add(previewSurface);
            surfaceList.add(yuvSurface);
            surfaceList.add(pictureSurface);

            //创建拍照模式捕获请求CameraDevice.TEMPLATE_STILL_CAPTURE
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(yuvSurface);


            // 创建预览的捕获会话
            mCameraDevice.createCaptureSession(surfaceList, pictureStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (DEFAULT_ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    public void onResume() {
        MyLogUtil.e(TAG, "onResume");
        startBackgroundThread();
        // 当屏幕关闭后重新打开, 若SurfaceTexture已经就绪, 此时onSurfaceTextureAvailable不会被回调, 这种情况下
        // 如果SurfaceTexture已经就绪, 则直接打开相机, 否则等待SurfaceTexture已经就绪的回调
        if (autoFitTextureView.isAvailable()) {
            openCamera();
        } else {
            autoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

    }

    public void onPause() {
        MyLogUtil.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
    }

    public void onDestroy() {

    }

    //设置相机的输出, 以支持全屏预览
    private void getCamera(int facing) {
        MyLogUtil.e(TAG, "getCamera");
        try {
            // 遍历设备的所有摄像头
            if (mCameraManager != null) {
                for (String cameraId : mCameraManager.getCameraIdList()) {
                    mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
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
                    fpsRanges = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    //输出图像需要旋转的顺时针角度
                    mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    //硬件级别
                    hardwareLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    //计算出最适合的预览尺寸（实际从相机得到的尺寸）
                    //map.getOutputSizes(SurfaceTexture.class)表示SurfaceTexture支持的尺寸List
                    mPreviewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class));

                    mVideoSize = getVideoSize(map.getOutputSizes(MediaRecorder.class));

                    mPictureSize = getPreviewSize(map.getOutputSizes(ImageFormat.JPEG));
                    formats = map.getOutputFormats();

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
                    MyLogUtil.e(TAG, "相机信息: 支持的格式formats = " + Arrays.toString(formats));// [32=RAW_SENSOR, 256=JPEG, 34=PRIVATE, 35=YUV_420_888]
                    MyLogUtil.e(TAG, "相机信息: 帧速率fpsRanges = " + Arrays.toString(fpsRanges));//[[12, 15], [15, 15], [14, 20], [20, 20], [14, 25], [25, 25], [14, 30], [30, 30]]
                    MyLogUtil.e(TAG, "相机信息: 相机方向mOrientation = " + mSensorOrientation);//270
                    MyLogUtil.e(TAG, "相机信息：预览宽高mPreviewSize = " + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());  // 1280*960
                    MyLogUtil.e(TAG, "相机信息：视频宽高mVideoSize = " + mVideoSize.getWidth() + "*" + mVideoSize.getHeight());  // 1280*960
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

    private static Size getVideoSize(Size[] choices) {
        MyLogUtil.e(TAG, "getVideoSize");
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        MyLogUtil.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    //计算出最适合全屏预览的尺寸
    private Size getPreviewSize(Size[] choices) {
        MyLogUtil.e(TAG, "getPreviewSize");
        // 获取当前的屏幕尺寸, 放到一个点对象里
        Point screenSize = new Point();
        ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(screenSize);
        int screenWidth = screenSize.x;  // 1080
        int screenHeight = screenSize.y; // 2160

        int displayRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
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
            MyLogUtil.e(TAG, "宽高比 " + reqRatio);

            float curRatio, deltaRatio;
            float deltaRatioMin = Float.MAX_VALUE;
            for (Size option : choices) {
                if (option.getWidth() < 240) continue;//1024表示可接受的最小尺寸，否则图像会很模糊，可以随意修改
                if (Math.max(option.getWidth(), option.getHeight()) > MAX_PREVIEW_WIDTH + 1 ||
                        Math.min(option.getWidth(), option.getHeight()) > MAX_PREVIEW_HEIGHT + 1) {
                    continue;
                }//不能超过这个，否则录制视频会花
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

    //打开相机
    private void openCamera() {
        MyLogUtil.e(TAG, "openCamera");
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
            if (mCameraManager != null) {
                mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    //关闭正在使用的相机
    private void closeCamera() {
        MyLogUtil.e(TAG, "closeCamera");
        try {
            // 获得相机开打关闭许可
            mCameraOpenCloseLock.acquire();
            // 关闭捕获会话
            if (mCameraCaptureSession != null) {
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
            // 关闭当前相机
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }

            if (mMediaRecorder != null) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            if (pictureImageReader != null) {
                pictureImageReader.close();
                pictureImageReader = null;
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

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }

    //设置录制视频参数
    public void setMediaRecorderConfig() {
        MyLogUtil.e(TAG, "setMediaRecorderConfig");
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);//camera2
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(8 * mVideoSize.getWidth() * mVideoSize.getHeight());
        Surface surface = new Surface(autoFitTextureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(mContext);
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        int rotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //是否正在录制视频
    public boolean isRecording() {
        return isRecording;
    }

    public boolean isRecordVideo() {
        return isRecordVideo;
    }

    //开始录制
    public void startRecord() {
        MyLogUtil.e(TAG, "startRecord");
        if (isRecordVideo) {
            createPreviewAndRecordAndImageReaderSession();
        }
    }

    //结束录制
    public void stopRecord() {
        MyLogUtil.e(TAG, "stopRecord");
        if (isRecordVideo) {
            isRecording = false;
            mMediaRecorder.stop();
            mMediaRecorder.reset();

            MyLogUtil.e(TAG, "视频录制 " + mNextVideoAbsolutePath);///storage/emulated/0/Android/data/com.example.tdycamera/files/1635497535274.mp4
            mNextVideoAbsolutePath = null;
            createPreviewAndImageReaderSession();
        }

    }

    //是否是前置相机
    public boolean isFrontCamera() {
        return isFrontCamera;
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
//        // 关闭当前相机
//        if (mCameraDevice != null) {
//            mCameraDevice.close();
//            mCameraDevice = null;
//        }
        closeCamera();
        openCamera();
    }

    //拍照
    public void takePicture() {
        MyLogUtil.e(TAG, "takePicture");
        try {
            mCameraCaptureSession.stopRepeating();
            mCameraCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        capture();
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        boolean mFlashSupported = available == null ? false : available;
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    //锁定焦点
    private void capture() {
        MyLogUtil.e(TAG, "capture");
        createPreviewAndPictureAndImageReaderSession();
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
