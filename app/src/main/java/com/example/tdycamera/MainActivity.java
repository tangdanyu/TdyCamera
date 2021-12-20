package com.example.tdycamera;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.example.tdycamera.demo.apicamera.CameraApiActivity;
import com.example.tdycamera.mycamera.camera1.Camera1Activity;
import com.example.tdycamera.mycamera.camera2.Camera2Activity;
import com.example.tdycamera.mycamera.camera2.basic.Camera2BasicActivity;
import com.example.tdycamera.mycamera.camera2.video.Camera2VideoActivity;
import com.example.tdycamera.mycamera.camerax.CameraXActivity;
import com.example.tdycamera.mycamera.opencv.OpencvCamera1Activity;
import com.example.tdycamera.mycamera.opencv.OpencvCamera2Activity;
import com.example.tdycamera.phonecamera.PhoneCameraActivity;
import com.example.tdycamera.record.RecordAudioActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback  {
    private String TAG = getClass().getSimpleName();

    private Button btnPhoneCamera;
    private Button btnCameraApi;
    private Button btnCamera1;
    private Button btnCamera2;
    private Button btnCamera2Video;
    private Button btnCamera2Basic;
    private Button btnCameraxDemo;
    private Button btnRecordAudio;
    private Button btnOpencvCamera1;
    private Button btnOpencvCamera2;


    private int REQUEST_CAMERA_PERMISSION = 100;
    private String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermissions(PERMISSIONS, REQUEST_CAMERA_PERMISSION);
        initView();
        initListener();
    }
    private void initView(){
        btnPhoneCamera = findViewById(R.id.btn_phoneCamera);
        btnCameraApi = findViewById(R.id.btn_camera_api);
        btnCamera1 = findViewById(R.id.btn_camera1);
        btnCamera2 = findViewById(R.id.btn_camera2);
        btnCamera2Video = findViewById(R.id.btn_camera2_video);
        btnCamera2Basic = findViewById(R.id.btn_camera2_basic);
        btnCameraxDemo = findViewById(R.id.btn_camerax_demo);
        btnRecordAudio = findViewById(R.id.btn_record_audio);
        btnOpencvCamera1 = findViewById(R.id.btn_opencv_camera1);
        btnOpencvCamera2 = findViewById(R.id.btn_opencv_camera2);
    }
    private void initListener(){
        btnPhoneCamera.setOnClickListener(this);
        btnCameraApi.setOnClickListener(this);
        btnCamera1.setOnClickListener(this);
        btnCamera2.setOnClickListener(this);
        btnCamera2Video.setOnClickListener(this);
        btnCamera2Basic.setOnClickListener(this);
        btnCameraxDemo.setOnClickListener(this);
        btnRecordAudio.setOnClickListener(this);
        btnOpencvCamera1.setOnClickListener(this);
        btnOpencvCamera2.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_phoneCamera:
                startActivity(new Intent(this, PhoneCameraActivity.class));
                break;
            case R.id.btn_camera_api:
                startActivity(new Intent(this, CameraApiActivity.class));
                break;
            case R.id.btn_camera1:
                startActivity(new Intent(this, Camera1Activity.class));
                break;
            case R.id.btn_camera2:
                startActivity(new Intent(this, Camera2Activity.class));
                break;
            case R.id.btn_camera2_video:
                startActivity(new Intent(this, Camera2VideoActivity.class));
                break;
            case R.id.btn_camera2_basic:
                startActivity(new Intent(this, Camera2BasicActivity.class));
                break;
            case R.id.btn_camerax_demo:
                startActivity(new Intent(this, CameraXActivity.class));
                break;
            case R.id.btn_record_audio:
                startActivity(new Intent(this, RecordAudioActivity.class));
                break;
            case R.id.btn_opencv_camera1:
                startActivity(new Intent(this, OpencvCamera1Activity.class));
                break;
            case R.id.btn_opencv_camera2:
                startActivity(new Intent(this, OpencvCamera2Activity.class));
                break;
        }
    }

    //动态申请权限
    protected Boolean checkAndRequestPermissions(String[] permissions, int requestCode) {
        List<String> requestPermission = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {//检查是否有了权限
                //没有权限即动态申请
                requestPermission.add(permission);
            }
        }

        if (requestPermission.size() == 0) {
            return true;
        }

        ActivityCompat.requestPermissions(this, requestPermission.toArray(new String[requestPermission.size()]), requestCode);
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isAllGrant = true;

        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle("提示").setMessage("权限被禁止。\n请在【设置-应用信息-权限】中重新授权").setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        onPermissionReject(requestCode);
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        onPermissionReject(requestCode);
                    }
                }).create();
                dialog.setCancelable(false);
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();

                isAllGrant = false;
                break;
            }
        }

        if (isAllGrant) {
            onPermissionGranted(requestCode);
        }
    }

    //获得全部权限
    public void onPermissionGranted(int requestCode) {
        Log.e(TAG,"已经获得权限");
    }
    //权限被拒绝
    public void onPermissionReject(int requestCode) {
        finish();
    }

}