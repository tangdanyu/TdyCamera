package com.example.tdycamera.mycamera.camerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.example.tdycamera.utils.MyLogUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraXActivity extends AppCompatActivity {
    private String TAG = "CameraXActivity";
    private int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private PreviewView previewView;

    private ExecutorService cameraExecutor;
    private double RATIO_4_3_VALUE = 4.0 / 3.0;
    private double RATIO_16_9_VALUE = 16.0 / 9.0;
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
        initCamerax();
        startCameraX();
//        cameraxTakePhoto();
        cameraxAnalysis();
    }

    private void initCamerax() {
        previewView = findViewById(R.id.previewView);
        preview = new Preview.Builder().build();
        mVideoCapture = new VideoCapture.Builder().build();
        imageCapture = new ImageCapture.Builder().build();
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                .build();
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void startCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("WrongConstant")
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, imageAnalysis, preview);
    }

    private void cameraxTakePhoto() {
        String path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + System.currentTimeMillis() + ".jpg";
        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(new File(path)).build();
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults outputFileResults) {
                        MyLogUtil.e(TAG, "onImageSaved : " + path);
                    }

                    @Override
                    public void onError(ImageCaptureException error) {


                    }
                });

//        imageCapture.takePicture(executor, new ImageCapture.OnImageCapturedCallback() {
//            @Override
//            public void onCaptureSuccess(@NonNull ImageProxy im) {
////                Bitmap bitmap = BitmapUtil.imageToBitMap(im.getImage());
////                ((Activity) context).runOnUiThread(new Runnable() {
////                    @Override
////                    public void run() {
////                        if (takePicBack != null) {
////                            takePicBack.takePicBack(bitmap);
////                        }
////                    }
////                });
//                super.onCaptureSuccess(im);
//            }
//        });
    }

    private void cameraxAnalysis() {
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
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            //IRequestFinishCallback$Stub中Android Q内存泄漏问题的解决方法。
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

}
