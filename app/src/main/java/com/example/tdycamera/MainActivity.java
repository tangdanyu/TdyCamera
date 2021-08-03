package com.example.tdycamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback  {
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
    private String currentVideoPath;//录像存储路径

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }
    private void initView(){
        btnTakePicture1 = findViewById(R.id.btn_takePicture1);
        btnTakePicture2 = findViewById(R.id.btn_takePicture2);
        btnTakeVideo = findViewById(R.id.btn_takeVideo);
        ivThumbnail = findViewById(R.id.iv_thumbnail);
        ivComplete = findViewById(R.id.iv_complete);
        vvVideo = findViewById(R.id.vv_video);
        btnTakePicture1.setOnClickListener(this);
        btnTakePicture2.setOnClickListener(this);
        btnTakeVideo.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_takePicture1:
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
//                setPic();
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
//            vvVideo.setVideoPath(currentVideoPath);
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
        String imageFileName = "Video_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);//需要在file_paths.xml中配置Movies
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".mp4",         /* 后缀 */
                storageDir      /* 目录 */
        );
        currentVideoPath = image.getAbsolutePath();
        return image;
    }
    //对调整后的图片进行解码
    private void setPic() {

        int targetW = ivComplete.getWidth();
        int targetH = ivComplete.getHeight();

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        //确定要缩小图像的比例
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // 将图像文件解码为位图大小以填充视图
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        ivComplete.setImageBitmap(bitmap);
    }

}