package com.example.tdycamera.mycamera.camerax;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.example.tdycamera.listener.CameraListener;
import com.example.tdycamera.utils.MyLogUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CameraXHelper {
    private String TAG = "CameraXHelper";
    private Context context;
    private PreviewView previewView;
    private CameraListener cameraListener;
    private Executor executor = Executors.newSingleThreadExecutor();
    private Camera camera;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private VideoCapture mVideoCapture;
    private boolean bRecording;
    private Preview preview;

    public CameraXHelper(Context context, PreviewView previewView, CameraListener cameraListener) {
        this.context = context;
        this.previewView = previewView;
        this.cameraListener = cameraListener;
        init();
    }

    private void init(){
        preview = new Preview.Builder().build();
        imageCapture = new ImageCapture.Builder()
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .build();
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(previewView.getWidth(), previewView.getHeight()))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
    }

    public void startCameraX() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    @SuppressLint("WrongConstant")
    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        cameraProvider.unbindAll();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        camera = cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, preview);
        cameraProvider.bindToLifecycle((LifecycleOwner) context, cameraSelector, imageCapture, imageAnalysis, preview);
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public void cameraxTakePhoto() {
        imageCapture.takePicture(executor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy im) {
//                Bitmap bitmap = BitmapUtil.imageToBitMap(im.getImage());
//                ((Activity) context).runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        if (takePicBack != null) {
//                            takePicBack.takePicBack(bitmap);
//                        }
//                    }
//                });
                super.onCaptureSuccess(im);
            }
        });
    }

    public void cameraDestroy(){
        MyLogUtil.e(TAG ,"cameraDestroy");
        if(previewView!=null){
            previewView = null;
        }
    }
}
