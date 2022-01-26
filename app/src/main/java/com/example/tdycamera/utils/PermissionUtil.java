package com.example.tdycamera.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionUtil {
    interface PermissionListener {
        void onPermissionSuccess();

        void onPermissionFailed();
    }

    private PermissionListener mPermissionListener;

    //动态申请权限
    public Boolean checkAndRequestPermissions(Context context, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT < 23) {//6.0才用动态权限
            return true;
        }

        List<String> requestPermission = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {//检查是否有了权限
                //没有权限即动态申请
                requestPermission.add(permission);
            }
        }

        if (requestPermission.size() == 0) {
            return true;
        }

        ActivityCompat.requestPermissions((Activity) context, requestPermission.toArray(new String[requestPermission.size()]), requestCode);
        return false;
    }

    public void chekPermissions(Activity context, String[] permissions, int requestCode, PermissionListener permissionListener) {
        mPermissionListener = permissionListener;

        if (Build.VERSION.SDK_INT < 23) {//6.0才用动态权限
            mPermissionListener.onPermissionSuccess();
            return;
        }
        List<String> requestPermission = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {//检查是否有了权限
                //没有权限即动态申请
                requestPermission.add(permission);
            }
        }

        if (requestPermission.size() == 0) {
            mPermissionListener.onPermissionSuccess();

        }

        //申请权限
        if (requestPermission.size() > 0) {//有权限没有通过，需要申请
            ActivityCompat.requestPermissions(context, permissions, requestCode);
        } else {
            //说明权限都已经通过，可以做你想做的事情去
            mPermissionListener.onPermissionSuccess();
        }
    }

}
