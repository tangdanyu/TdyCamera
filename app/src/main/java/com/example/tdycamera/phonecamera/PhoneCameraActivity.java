package com.example.tdycamera.phonecamera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import com.example.tdycamera.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PhoneCameraActivity extends AppCompatActivity  implements View.OnClickListener{
    private String TAG = getClass().getSimpleName();
    private static final int REQ_1 = 1;
    private static final int REQ_2 = 2;
    private static final int REQ_3 = 3;
    private ImageView ivThumbnail;
    private ImageView ivComplete;
    private VideoView vvVideo;
    private Button btnTakePicture1;
    private Button btnTakePicture2;
    private Button btnTakeVideo;
    private String currentPhotoPath;//拍摄照片路径
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_camera);
        initView();
        initListener();
    }
    private void initView(){
        btnTakePicture1 = findViewById(R.id.btn_takePicture1);
        btnTakePicture2 = findViewById(R.id.btn_takePicture2);
        btnTakeVideo = findViewById(R.id.btn_takeVideo);
        ivThumbnail = findViewById(R.id.iv_thumbnail);
        ivComplete = findViewById(R.id.iv_complete);
        vvVideo = findViewById(R.id.vv_video);


    }
    private void initListener(){
        btnTakePicture1.setOnClickListener(this);
        btnTakePicture2.setOnClickListener(this);
        btnTakeVideo.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_phoneCamera:
                takePictureGetThumbnail();
                break;
            case R.id.btn_takePicture2:
                takePictureGetFile();
                break;
            case R.id.btn_takeVideo:
                takeVideo();
                break;
        }
    }

    //调用相机应用拍照获得缩略图
    private void takePictureGetThumbnail() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQ_1);
        }
    }
    //调用相机应用拍照获得完整图片
    private void takePictureGetFile() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        this.getPackageName()+".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQ_2);
            }
        }
    }

    //调用相机应用录制视频
    private void takeVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {//如果是null,应用会崩溃
            File videoFile = null;
            try {
                videoFile = createVideoFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (videoFile != null) {
                Uri videoURI = FileProvider.getUriForFile(this,
                        this.getPackageName()+".fileprovider",
                        videoFile);
                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoURI);
                startActivityForResult(takeVideoIntent, REQ_3);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //获得缩略图
        if (requestCode == REQ_1 && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            ivThumbnail.setImageBitmap(imageBitmap);
        }
        //获得完整图片
        if (requestCode == REQ_2) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(currentPhotoPath);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                ivComplete.setImageBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
        //观看视频
        if (requestCode == REQ_3 && resultCode == RESULT_OK) {
            Uri videoUri = data.getData();
            vvVideo.setMediaController(new MediaController(this));
            vvVideo.setVideoURI(videoUri);
            vvVideo.start();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);//需要在file_paths.xml中配置Pictures
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".jpg",         /* 后缀 */
                storageDir      /* 目录 */
        );
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String videoFileName = "Video_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);//需要在file_paths.xml中配置Movies
        File video = File.createTempFile(
                videoFileName,  /* 前缀 */
                ".mp4",         /* 后缀 */
                storageDir      /* 目录 */
        );
        return video;
    }

}