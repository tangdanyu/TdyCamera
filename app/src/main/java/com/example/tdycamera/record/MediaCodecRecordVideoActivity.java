package com.example.tdycamera.record;

import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaCodec;
import android.os.Bundle;

import com.example.tdycamera.R;

import java.io.IOException;

public class MediaCodecRecordVideoActivity extends AppCompatActivity {
    private MediaCodec mediaCodec;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_codec_record_video);
    }
    private void initView(){
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}