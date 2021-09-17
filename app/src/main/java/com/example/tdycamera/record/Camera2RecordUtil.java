package com.example.tdycamera.record;

import android.content.Context;
import android.media.MediaRecorder;
import android.view.Surface;
import android.view.TextureView;

import com.example.tdycamera.utils.MyLogUtil;

import java.io.File;

public class Camera2RecordUtil {
    private String TAG = "Camera2RecordUtil";
    //录制视频
    private MediaRecorder mMediaRecorder;
    private Context context;

    public Camera2RecordUtil(Context context) {
        this.context = context;
        mMediaRecorder = new MediaRecorder();
    }

    public Surface getSurface(){
        return mMediaRecorder.getSurface();
    }
    public MediaRecorder getMediaRecorder(){
        return mMediaRecorder;
    }
    public void setMediaRecorderConfig(int width, int height , TextureView textureView) {
        MyLogUtil.e(TAG,"width height"+width+height);
//        mMediaRecorder.reset();
//            mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//camera1
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//camera2
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(height, width);
        mMediaRecorder.setOrientationHint(270);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(8 * width * height);
        Surface surface = new Surface(textureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);

    }

    //开始录制视频
    public void startRecord() {
        MyLogUtil.e(TAG,"startRecord");
        try {
            File file = new File(context.getExternalCacheDir(), "demo.mp4");
            if (file.exists()) {
                file.delete();
            }
            MyLogUtil.e(TAG,"file.getPath()"+file.getPath());
            mMediaRecorder.setOutputFile(file.getPath());
            mMediaRecorder.prepare();
            mMediaRecorder.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //停止录制视频
    public void stopRecord() {
        MyLogUtil.e(TAG,"stopRecord");
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
    }
    public void close(){
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }
}
