package com.example.tdycamera.mycamera.camerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.tdycamera.R;
import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.mycamera.camera1.Camera1SettingsActivity;
import com.example.tdycamera.utils.MyLogUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraXActivity extends AppCompatActivity implements View.OnClickListener {
    private String TAG = "CameraXActivity";
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;

    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private PreviewView previewView;
    private ImageView previewIv;
    private Button switchCameraBtn;
    private Button settingBtn;
    private Button takePictureBtn;
    private Button recordBtn;
    private ExecutorService cameraExecutor;
    private CameraListener cameraListener;
    private VideoCapture mVideoCapture;
    private boolean bRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_camerax);

        initView();
        initListener();
        initCameraXPreview();
        initCameraXAnalysis();
    }

    private void initView() {
        previewView = findViewById(R.id.previewView);
        previewIv = findViewById(R.id.preview_iv);
        switchCameraBtn = findViewById(R.id.switch_camera_btn);
        settingBtn = findViewById(R.id.setting_btn);
        takePictureBtn = findViewById(R.id.take_picture_btn);
        recordBtn = findViewById(R.id.record_btn);
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void initListener() {
        switchCameraBtn.setOnClickListener(this);
        settingBtn.setOnClickListener(this);
        takePictureBtn.setOnClickListener(this);
        recordBtn.setOnClickListener(this);
    }

    private void initCameraXPreview() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                // 摄像机提供商现在保证可用
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                cameraProvider.unbindAll();
                // 设置取景器用例以显示相机预览
                Preview preview = new Preview.Builder().build();
                // 设置取景器用例以显示相机预览
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                //通过要求镜头朝向来选择相机
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();
                // 将用例附加到具有相同生命周期所有者的相机
                Camera camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture);
                // 将预览用例连接到previewView
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    //拍照保存到文件
    private void cameraXCaptureToFile() {
        String path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + System.currentTimeMillis() + ".jpg";
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(new File(path)).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        MyLogUtil.e(TAG, "onImageSaved : " + path);
                        Bitmap bitmap = BitmapFactory.decodeFile(path);
                        Bitmap newImage = null;
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
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

                    @Override
                    public void onError(ImageCaptureException error) {
                    }
                });
    }

    //拍照返回视频帧
    private void cameraXCaptureToImage() {

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new
                ImageCapture.OnImageCapturedCallback() {
                    @RequiresApi(api = Build.VERSION_CODES.N)
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        super.onCaptureSuccess(image);
                        MyLogUtil.e(TAG, "onCaptureSuccess : " + image);
                        // 将 imageProxy 转为 byte数组
                        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                        // 新建指定长度数组
                        byte[] bytes = new byte[byteBuffer.remaining()];
//                        // 倒带到起始位置 0
//                        byteBuffer.rewind();
//                        // 数据复制到数组, 这个 byteArray 包含有 exif 相关信息，
//                        // 由于 bitmap 对象不会包含 exif 信息，所以转为 bitmap 需要注意保存 exif 信息
                        byteBuffer.get(bytes);
//                        // 获取照片 Exif 信息
//                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
//                        ExifInterface exif = null;
//                        try {
//                            exif = new ExifInterface(byteArrayInputStream);
//                            String direction =exif.getAttribute(ExifInterface.TAG_ORIENTATION);   //获取图片方向
//                            MyLogUtil.e(TAG,"获取图片方向"+direction);
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Bitmap newImage = null;
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
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
                        //使用完image关闭
                        image.close();

                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        super.onError(exception);

                    }
                });


    }

    private void initCameraXAnalysis() {
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                .build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy image) {
                int rotationDegrees = image.getImageInfo().getRotationDegrees();
                image.close();
            }
        });
    }

    @SuppressLint("RestrictedApi")
    public void recordVideo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (bRecording) {
            bRecording = false;
            if (mVideoCapture != null) {
                mVideoCapture.stopRecording();
            }
            return;
        } else {
            bRecording = true;
        }

        String path = getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath() + File.separator + System.currentTimeMillis() + ".mp4";
        MyLogUtil.e(TAG, "录制开始:" + path);
        VideoCapture.OutputFileOptions outputFileOptions = new VideoCapture.OutputFileOptions.Builder(new File(path)).build();
        mVideoCapture = new VideoCapture.Builder().build();
        mVideoCapture.startRecording(outputFileOptions, ContextCompat.getMainExecutor(this),
                new VideoCapture.OnVideoSavedCallback() {
                    /**
                     * Called when the video has been successfully saved.
                     *
                     * @param outputFileResults
                     */
                    @Override
                    public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                        MyLogUtil.e(TAG, "onVideoSaved 录制成功:" + path);
                    }

                    @Override
                    public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        MyLogUtil.e(TAG, "onError 录制出错:" + message);
                        bRecording = false;
                    }
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        //请求相机权限
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
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
                lensFacing = (lensFacing == CameraSelector.LENS_FACING_FRONT) ?
                        CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
                initCameraXPreview();
                break;
            case R.id.setting_btn:

                break;
            case R.id.take_picture_btn:
                cameraXCaptureToFile();
//                cameraXCaptureToImage();
                break;
            case R.id.record_btn:

                break;
        }
    }

}
