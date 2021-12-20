package com.example.tdycamera.mycamera.opencv;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.util.AttributeSet;
import android.util.Log;

import com.example.tdycamera.demo.api.Camera2;
import com.example.tdycamera.utils.MyLogUtil;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCamera2View;


public class OpencvCamera2Helper extends JavaCamera2View {

    private static final String TAG = "OpencvCamera1Helper";
    private String mPictureFileName;
    public OpencvCamera2Helper(Context context, int cameraId) {
        super(context, cameraId);
    }

    public OpencvCamera2Helper(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void takePicture(final String fileName) {
        Log.i(TAG, "Taking picture");
        this.mPictureFileName = fileName;

    }
    //切换摄像头，要先停止再开启
    public void switchCamera() {
        MyLogUtil.e(TAG, "切换摄像头 1 " +mCameraIndex);
        if ( mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK ){
            mCameraIndex = CameraBridgeViewBase.CAMERA_ID_FRONT;
        }else {
            mCameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK;
        }
        mCameraIndex = (mCameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK ) ?
                CameraBridgeViewBase.CAMERA_ID_BACK :  CameraBridgeViewBase.CAMERA_ID_FRONT;
        MyLogUtil.e(TAG, "切换摄像头 2 " +mCameraIndex);
        disconnectCamera();
        disableView();
        enableView();
    }
    //是否是前置摄像头
    public boolean isFrontCamera() {
        return  mCameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT || mCameraIndex == 1;
    }

}
