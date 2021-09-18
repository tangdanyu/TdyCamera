package com.example.tdycamera.mnn;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Switch;
import android.widget.TextView;

import com.alibaba.android.mnnkit.entity.FaceDetectionReport;
import com.example.tdycamera.R;
import com.example.tdycamera.utils.MyLogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MNNDrawUtil {
    private String TAG = "MNNDrawUtil";
    // 当前渲染画布的尺寸
    protected int mActualPreviewWidth;
    protected int mActualPreviewHeight;
    private int width;
    private int height;
    private boolean screenAutoRotate ;

    private Paint KeyPointsPaint = new Paint();
    private Paint PointOrderPaint = new Paint();
    private Paint ScorePaint = new Paint();
    private Canvas canvas;     // 画布
    private final static int MAX_RESULT = 10;
    private float[] scores = new float[MAX_RESULT];// 置信度
    private float[] rects = new float[MAX_RESULT * 4];// 矩形区域
    private float[] facePoints = new float[MAX_RESULT * 2 * 106];// 脸106关键点

    private Activity mActivity;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;  // 用于画框的surfaceView的holder
    private Switch mOrderSwitch;
    private TextView mTimeCost;
    private TextView mFaceAction;
    private TextView mYPR;
    private Handler handler = new Handler(Looper.getMainLooper());


    public MNNDrawUtil(Activity activity,int width,int height,int actualPreviewWidth,int actualPreviewHeight,boolean screenAutoRotate){
        this.mActivity = activity;
        surfaceView = activity.findViewById(R.id.surface_view_draw);
        // 设置SurfaceView
        surfaceView.setZOrderOnTop(true);  // 设置surfaceView在顶层
        surfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT); // 设置surfaceView为透明
        surfaceHolder = surfaceView.getHolder();  // 获取surfaceHolder以便后面画框
        surfaceHolder.setFixedSize(width, height);
        // 点序
        mOrderSwitch = activity.findViewById(R.id.swPointOrder);
        mTimeCost = activity.findViewById(R.id.costTime);
        mFaceAction = activity.findViewById(R.id.faceAction);
        mYPR = activity.findViewById(R.id.ypr);
        this.width = width;
        this.height = height;
        this.mActualPreviewWidth = actualPreviewWidth;//如果方向正确，但是矩形框较长，在下方，则是因为宽高反了
        this.mActualPreviewHeight = actualPreviewHeight;
        this.screenAutoRotate = screenAutoRotate;

        KeyPointsPaint.setColor((Color.WHITE));
        KeyPointsPaint.setStyle(Paint.Style.FILL);
        KeyPointsPaint.setStrokeWidth(2);

        PointOrderPaint.setColor(Color.GREEN);
        PointOrderPaint.setStyle(Paint.Style.STROKE);
        PointOrderPaint.setStrokeWidth(2f);
        PointOrderPaint.setTextSize(18);

        ScorePaint.setColor(Color.WHITE);
        ScorePaint.setStrokeWidth(2f);
        ScorePaint.setTextSize(40);

    }
    long start = System.currentTimeMillis();
    public void drawResult( int cameraOrientation,int rotateDegree,FaceDetectionReport[] results){

        if (results!=null && results.length>0) {
            for (int i=0; i<results.length&&i<MAX_RESULT; i++) {
                // key points
                System.arraycopy(results[i].keyPoints, 0, facePoints, i*106*2, 106*2);
                // face rect
                rects[i*4] = results[i].rect.left;
                rects[i*4+1] = results[i].rect.top;
                rects[i*4+2] = results[i].rect.right;
                rects[i*4+3] = results[i].rect.bottom;
                // score
                scores[i] = results[i].score;
            }
            String yprText ;

            FaceDetectionReport firstReport = results[0];
            yprText = "yaw: " + firstReport.yaw + "\npitch: " + firstReport.pitch + "\nroll: " + firstReport.roll + "\n";
            String   faceActionText = faceActionDesc(firstReport.faceActionMap);
            MyLogUtil.e(TAG,faceActionText);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mYPR.setText(yprText);
                    mFaceAction.setText(faceActionText);
                }
            });


            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas == null) {
                    MyLogUtil.e("canvas == null");
                    return;
                }
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                float kx = 0.0f, ky = 0.0f;

                // 这里只写了摄像头正向为90/270度的一般情况，如果有其他情况，自行枚举
                if (90 == cameraOrientation || 270 == cameraOrientation) {

                    if (!screenAutoRotate) {
                        kx = ((float) mActualPreviewWidth) / height;
                        ky = (float) mActualPreviewHeight / width;
                    } else {
                        if ((0 == rotateDegree) || (180 == rotateDegree)) {// 屏幕竖直方向翻转
                            kx = ((float) mActualPreviewWidth) / height;
                            ky = ((float) mActualPreviewHeight) / width;
                        } else if (90 == rotateDegree || 270 == rotateDegree) {// 屏幕水平方向翻转
                            kx = ((float) mActualPreviewWidth) / width;
                            ky = ((float) mActualPreviewHeight) / height;
                        }
                    }
                }
                // 绘制人脸关键点
                for (int j = 0; j < 106; j++) {
                    float keyX = facePoints[j * 2];
                    float keyY = facePoints[j * 2 + 1];
                    canvas.drawCircle(keyX * kx, keyY * ky, 5.0f, KeyPointsPaint);
                    if (mOrderSwitch.isChecked()) {
                        canvas.drawText(j+"", keyX * kx, keyY * ky, PointOrderPaint); //标注106点的索引位置
                    }
                }
                float left = rects[0];
                float top = rects[1];
                float right = rects[2];
                float bottom = rects[3];
                canvas.drawLine(left * kx, top * ky,
                        right * kx, top * ky, KeyPointsPaint);
                canvas.drawLine(right * kx, top * ky,
                        right * kx, bottom * ky, KeyPointsPaint);
                canvas.drawLine(right * kx, bottom * ky,
                        left * kx, bottom * ky, KeyPointsPaint);
                canvas.drawLine(left * kx, bottom * ky,
                        left * kx, top * ky, KeyPointsPaint);

            } catch (Throwable t) {

            } finally {
                if (canvas != null) {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }
            }
        }

    }
    // 清除特征点
    public void drawClear() {
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                MyLogUtil.e("canvas == null");
                return;
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        } catch (Throwable t) {

        } finally {
            if (canvas != null) {
                surfaceHolder.unlockCanvasAndPost(canvas);
            }
        }
    }
    public static String faceActionDesc(Map<String, Boolean> faceActionMap) {

        String desc = "";
        if (faceActionMap.size()==0) {
            return desc;
        }

        List<String> actions = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : faceActionMap.entrySet()) {
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());

            Boolean bActing = entry.getValue();
            if (!bActing) continue;

            if (entry.getKey().equals("HeadYaw")) {
                actions.add("摇头");
            }
            if (entry.getKey().equals("BrowJump")) {
                actions.add("眉毛挑动");
            }
            if (entry.getKey().equals("EyeBlink")) {
                actions.add("眨眼");
            }
            if (entry.getKey().equals("MouthAh")) {
                actions.add("嘴巴大张");
            }
            if (entry.getKey().equals("HeadPitch")) {
                actions.add("点头");
            }
        }

        for (int i=0; i<actions.size(); i++) {
            String action = actions.get(i);
            if (i>0) {
                desc += "、"+action;
                continue;
            }
            desc = action;
        }

        return desc;
    }
    public void close(){

    }
}
