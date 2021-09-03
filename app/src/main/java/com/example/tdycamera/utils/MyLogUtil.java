package com.example.tdycamera.utils;

import android.util.Log;

import com.example.tdycamera.BuildConfig;


/**
 * @author Gao
 * Log日志
 */
public class MyLogUtil {

    private static final String TAG = "MyLogUtil java ";

    private static final boolean isDebug = BuildConfig.DEBUG;
    private static final int DEBUG = 1;
    private static final int INFO = 2;
    private static final int WARN = 3;
    private static final int ERROR = 4;

    public static void d(String log) {
        if (isDebug) {
            Log.d(TAG, log);
        }
    }

    public static void i(String log) {
        if (isDebug) {
            Log.i(TAG, log);
        }
    }

    public static void w(String log) {
        if (isDebug) {
            Log.w(TAG, log);
        }
    }

    public static void e(String log) {
        if (isDebug) {
            Log.e(TAG, log);
        }
    }

    public static void d(String tag, String log) {
        if (isDebug) {
            Log.d(TAG+tag, log);
        }
    }

    public static void i(String tag, String log) {
        if (isDebug) {
            Log.i(TAG+tag, log);
        }
    }

    public static void w(String tag, String log) {
        if (isDebug) {
            Log.w(TAG+tag, log);
        }
    }

    public static void e(String tag, String log) {
        if (isDebug) {
            Log.e(TAG+tag, log);
        }
    }
}
