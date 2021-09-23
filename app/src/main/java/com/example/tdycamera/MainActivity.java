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

import com.example.tdycamera.apicamera.CameraApiActivity;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity  implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback  {
    private String TAG = getClass().getSimpleName();
    private Button btnCameraApi;


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
        btnCameraApi = findViewById(R.id.btn_camera_api);
    }
    private void initListener(){
        btnCameraApi.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_camera_api:
                startActivity(new Intent(this, CameraApiActivity.class));
                break;
        }
    }

    //动态申请权限
    protected Boolean checkAndRequestPermissions(String[] permissions, int requestCode) {
        List<String> requestPermission = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {//检查是否有了权限
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

    public void onPermissionGranted(int requestCode) {
        Log.e(TAG,"已经获得权限");
    }
    public void onPermissionReject(int requestCode) {
        finish();
    }

}