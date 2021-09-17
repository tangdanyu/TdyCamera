package com.example.tdycamera.mnn;

public interface MNNFaceDetectListener {
    //识别到人脸，可能有多个
    default void onFaceDetected(int faceNumber){}
    //未识别到人脸
    void onNoFaceDetected();
}
