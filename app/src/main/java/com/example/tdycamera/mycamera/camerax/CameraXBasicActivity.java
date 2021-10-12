package com.example.tdycamera.mycamera.camerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
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

public class CameraXBasicActivity extends AppCompatActivity {
    private String TAG = "CameraXBasicActivity";
    private PreviewView previewView;
    private CameraListener cameraListener;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private Camera camera;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
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

        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
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
}
