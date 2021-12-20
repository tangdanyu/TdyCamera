package com.example.tdycamera.mnn;

import android.content.Context;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.alibaba.android.mnnkit.actor.FaceDetector;
import com.alibaba.android.mnnkit.entity.FaceDetectConfig;
import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.alibaba.android.mnnkit.entity.MNNCVImageFormat;
import com.alibaba.android.mnnkit.entity.MNNFlipType;
import com.alibaba.android.mnnkit.intf.InstanceCreatedListener;


public class MNNFaceDetectorAdapter {
    private static final String TAG = "MNNFaceDetectorAdapter";
    private FaceDetector mFaceDetector = null;
    private MNNFaceDetectListener mnnFaceDetectListener = null;

    public MNNFaceDetectorAdapter(Context context, MNNFaceDetectListener mnnFaceDetectListener) {
        if (null == context) {
            return;
        }
        // 创建Kit实例
        FaceDetector.FaceDetectorCreateConfig createConfig = new FaceDetector.FaceDetectorCreateConfig();
        createConfig.mode = FaceDetector.FaceDetectMode.MOBILE_DETECT_MODE_VIDEO;
        FaceDetector.createInstanceAsync(context, createConfig, new InstanceCreatedListener<FaceDetector>() {
            @Override
            public void onSucceeded(FaceDetector faceDetector) {
                mFaceDetector = faceDetector;
            }

            @Override
            public void onFailed(int i, Error error) {
                Log.e(TAG, "create face detetector failed: " + error);
            }
        });
        this.mnnFaceDetectListener = mnnFaceDetectListener;
    }

    @NonNull
    public FaceDetectionReport[] getFace(Bitmap bitmap, int inAngle, int outAngle, boolean isFrontCamera) {
        if (null == bitmap || null == mFaceDetector) {
            return null;
        }
        FaceDetectionReport[] results = null;
        long detectConfig = 0;//关于眨眼张嘴等配置
        MNNFlipType outputFlip = isFrontCamera ? MNNFlipType.FLIP_Y : MNNFlipType.FLIP_NONE;//是否翻转
        results = mFaceDetector.inference(bitmap, detectConfig, inAngle, outAngle, outputFlip);
        if (results != null && results.length > 0) {
            return results;
        } else {
            if (mnnFaceDetectListener != null) {
                mnnFaceDetectListener.onNoFaceDetected();
            }
        }
        return null;
    }

    @NonNull
    public FaceDetectionReport[] getFace(byte[] bytebuffer, int width, int height, int imageFormat, int inAngle, int outAngle, boolean isFrontCamera) {
        if (null == bytebuffer || null == mFaceDetector) {
            return null;
        }
        FaceDetectionReport[] results = null;
        //关于眨眼张嘴等配置
//        long detectConfig =0;不配置
        long detectConfig = FaceDetectConfig.ACTIONTYPE_EYE_BLINK | FaceDetectConfig.ACTIONTYPE_MOUTH_AH | FaceDetectConfig.ACTIONTYPE_HEAD_YAW | FaceDetectConfig.ACTIONTYPE_HEAD_PITCH | FaceDetectConfig.ACTIONTYPE_BROW_JUMP;
        MNNFlipType outputFlip = isFrontCamera ? MNNFlipType.FLIP_Y : MNNFlipType.FLIP_NONE;//是否翻转
        if (imageFormat == MNNCVImageFormat.YUV_NV21.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.YUV_NV21, detectConfig, inAngle, outAngle, outputFlip);
        } else if (imageFormat == MNNCVImageFormat.RGBA.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.RGBA, detectConfig, inAngle, outAngle, outputFlip);
        } else if (imageFormat == MNNCVImageFormat.RGB.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.RGB, detectConfig, inAngle, outAngle, outputFlip);

        } else if (imageFormat == MNNCVImageFormat.BGR.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.BGR, detectConfig, inAngle, outAngle, outputFlip);
        } else if (imageFormat == MNNCVImageFormat.GRAY.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.GRAY, detectConfig, inAngle, outAngle, outputFlip);
        } else if (imageFormat == MNNCVImageFormat.BGRA.format) {
            results = mFaceDetector.inference(bytebuffer, width, height,
                    MNNCVImageFormat.BGRA, detectConfig, inAngle, outAngle, outputFlip);
        }
        if (results != null && results.length > 0) {
            return results;
        } else {
            if (mnnFaceDetectListener != null) {
                mnnFaceDetectListener.onNoFaceDetected();
            }
        }
        return null;
    }

    public void close() {
        if (mFaceDetector != null) {
            mFaceDetector.release();
            mFaceDetector = null;
        }
        if (mnnFaceDetectListener != null) {
            mnnFaceDetectListener = null;
        }
    }

}
