package com.example.tdycamera.record;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

/**
 * 录制参数封装
 */
public class VideoRecordParams {
    /**
     * camera
     * cameraId
     * mSurfaceTexture
     * videoWidth      摄像头预览宽度,注意是否是倒置的
     * videoHeight     摄像头预览高度
     * videoRotate      视频旋转角度
     * videoFrameRate   视频帧率
     * bitRate          视频码率
     * outputPath       视频输出路径
     * pixelFormat      录制的像素格式
     */

    private Camera camera;
    private int cameraId;
    private SurfaceTexture surfaceTexture;
    private int videoWidth;
    private int videoHeight;
    private int videoRotate;
    private int videoFrameRate = 30;
    private long bitRate;
    private String outputPath;
    private int pixelFormat ;

    public Camera getCamera() {
        return camera;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public int getCameraId() {
        return cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        this.surfaceTexture = surfaceTexture;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public void setVideoWidth(int videoWidth) {
        this.videoWidth = videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public void setVideoHeight(int videoHeight) {
        this.videoHeight = videoHeight;
    }

    public int getVideoRotate() {
        return videoRotate;
    }

    public void setVideoRotate(int videoRotate) {
        this.videoRotate = videoRotate;
    }

    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public void setVideoFrameRate(int videoFrameRate) {
        this.videoFrameRate = videoFrameRate;
    }

    public long getBitRate() {
        return bitRate;
    }

    public void setBitRate(long bitRate) {
        this.bitRate = bitRate;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }

    public int getPixelFormat() {
        return pixelFormat;
    }

    public void setPixelFormat(int pixelFormat) {
        this.pixelFormat = pixelFormat;
    }

    @Override
    public String toString() {
        return "VideoRecordParams{" +
                "camera=" + camera +
                ", surfaceTexture=" + surfaceTexture +
                ", videoWidth=" + videoWidth +
                ", videoHeight=" + videoHeight +
                ", videoRotate=" + videoRotate +
                ", videoFrameRate=" + videoFrameRate +
                ", bitRate=" + bitRate +
                ", outputPath='" + outputPath + '\'' +
                ", pixelFormat=" + pixelFormat +
                '}';
    }
}
