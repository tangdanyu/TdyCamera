package com.example.tdycamera.record;


import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.view.Surface;

import com.example.tdycamera.utils.MyLogUtil;

import java.io.File;
import java.io.IOException;


public class Camera1RecordUtil {
    private static final String TAG = "Camera1RecordUtil";
    private MediaRecorder mMediaRecorder;
    public Camera1RecordUtil() {
    }
    private void setVideoRecordParams(Context context,Camera camera,int Rotation, int height,int width,SurfaceTexture surfaceTexture,int cameraId){
        VideoRecordParams params = new VideoRecordParams();
        long currentTime = System.currentTimeMillis();
        String path =context.getExternalFilesDir("video").getAbsolutePath() + File.separator + "video_"+currentTime+"_.mp4";
        params.setCamera(camera);
        params.setVideoRotate(Rotation);
        params.setVideoHeight(height);
        params.setVideoWidth(width);
        params.setOutputPath(path);
        params.setSurfaceTexture(surfaceTexture);
        params.setCameraId(cameraId);
    }
    private void configMediaRecorder(VideoRecordParams params){
        File videoFile = new File( "video.mp4");
        MyLogUtil.e(TAG, "文件路径="+videoFile.getAbsolutePath());
        if (videoFile.exists()){
            videoFile.delete();
        }
        params.getCamera().unlock();
        mMediaRecorder.setCamera(params.getCamera());
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);//设置音频输入源  也可以使用 MediaRecorder.AudioSource.MIC
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);//设置视频输入源
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//音频输出格式
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);//设置音频的编码格式
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);//设置视频编码格式
        try {
            //推荐使用以下代码进行参数配置
            CamcorderProfile bestCamcorderProfile = getBestCamcorderProfile(params.getCameraId());
            mMediaRecorder.setProfile(bestCamcorderProfile);
        } catch (Exception e) {
            //设置输出格式
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);//音频输出格式
            //声音编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//设置音频的编码格式
            //视频编码格式
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);//视频编码格式
        }
        mMediaRecorder.setVideoFrameRate(30);//要录制的视频帧率 帧率越高视频越流畅 如果设置设备不支持的帧率会报错  按照注释说设备会支持自动帧率所以一般情况下不需要设置
        mMediaRecorder.setVideoSize(params.getVideoWidth(),params.getVideoHeight());//设置录制视频的分辨率  如果设置设备不支持的分辨率会报错
        mMediaRecorder.setVideoEncodingBitRate(8*params.getVideoWidth()*params.getVideoHeight()); //设置比特率,比特率是每一帧所含的字节流数量,比特率越大每帧字节越大,画面就越清晰,算法一般是 5 * 选择分辨率宽 * 选择分辨率高,一般可以调整5-10,比特率过大也会报错
        mMediaRecorder.setOrientationHint(params.getVideoRotate());  // 设置视频的摄像头角度 只会改变录制的视频文件的角度(对预览图像角度没有效果)前置270 后置90
        mMediaRecorder.setMaxDuration(30 * 1000); //设置记录会话的最大持续时间（毫秒）
        mMediaRecorder.setPreviewDisplay(new Surface(params.getSurfaceTexture()));//设置拍摄预览
        mMediaRecorder.setOutputFile(params.getOutputPath());//MP4文件保存路径

    }



    private void startRecord(VideoRecordParams params){
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }

        configMediaRecorder(params);//配置MediaRecorder  因为每一次停止录制后调用重置方法后都会取消配置,所以每一次开始录制都需要重新配置一次
        try {
            mMediaRecorder.prepare();//准备
            mMediaRecorder.start();//开启
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void stopRecord(){
        if(mMediaRecorder!= null){
            try {
                mMediaRecorder.stop();//暂停
                mMediaRecorder.reset();//重置 重置后将进入空闲状态,再次启动录制需要重新配置MediaRecorder
                mMediaRecorder.release();
                mMediaRecorder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private void pauseRecord(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mMediaRecorder.pause();//暂停
        }
    }
    private void resumeRecord(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mMediaRecorder.resume();//恢复
        }
    }
    public CamcorderProfile getBestCamcorderProfile(int cameraID){
        CamcorderProfile profile = CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_LOW);
        if(CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_480P)){
            //对比下面720 这个选择 每帧不是很清晰
            profile = CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_480P);
            profile.videoBitRate = profile.videoBitRate/5;
            return profile;
        }
        if(CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_720P)){
            //对比上面480 这个选择 动作大时马赛克!!
            profile = CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_720P);
            profile.videoBitRate = profile.videoBitRate/35;
            return profile;
        }
        if(CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_CIF)){
            profile = CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_CIF);
            return profile;
        }
        if(CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_QVGA)){
            profile = CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_QVGA);
            return profile;
        }
        return profile;
    }

    private void destroy(){
        if (mMediaRecorder != null){
            mMediaRecorder.stop();
            mMediaRecorder.release();//释放 释放之前需要先调用stop()
            mMediaRecorder = null;
        }
    }

}
