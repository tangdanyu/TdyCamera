package com.example.tdycamera.view;

import android.content.Context;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.TextureView;


/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class AutoFitTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 设置宽高比
     *
     * @param width
     * @param height
     */
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        //相机输出尺寸宽高默认是横向的，屏幕是竖向时需要反转
        // （后续适配屏幕旋转时会有更好的方案，这里先这样）

        mRatioWidth = width;
        mRatioHeight = height;
        //请求重新布局
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            //未设定宽高比，使用预览窗口默认宽高
            setMeasuredDimension(width, height);
        } else {
            //设定宽高比，调整预览窗口大小（调整后窗口大小不超过默认值）大于号不留白，全屏，小于号留白啊啊啊啊啊啊啊啊啊啊
            if (width > height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setTransform(transformSurface(width,height,mRatioWidth,mRatioHeight));
//                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);

            }
        }
    }
    /**
     * 根据实际预览的尺寸来计算Surface的缩放与移动大小
     * 必须把Surface显示的与实现拍摄的预览界面的比较一致, 先调整比例,再调整偏移, 这样预览的效果即不会拉伸,拍出来的效果也与实际一致
     * @param sw surface的宽
     * @param sh surface的高
     * @param prew Camera.Size 预览宽,注意相机的旋转90
     * @param preh Camera.Size 预览高,注意相机的旋转90
     * @return 返回的Matrix中包含着X轴或者Y轴的缩放比例与偏移
     */
    private Matrix transformSurface(int sw, int sh, int prew, int preh) {
        Matrix matrix = new Matrix();
        float preScale = preh / (float) prew;
        float viewScale = sh / (float) sw;
        if (preScale != viewScale) {//宽高比例不一样,才需要做处理
            if (preScale > viewScale) {//将高宽比例较大的放到屏幕上显示, 所以需要截掉预览的一部分高, 即Y轴偏移
                //按预览的宽与需要显示的宽比例调整预览的高度
                float scalePreY = sw * preScale;// preHeight * (sWidth / preWidth);
                //Y轴需要放大的比例
                matrix.preScale(1.0f, scalePreY / sh);
                float translateY = (sh - scalePreY) / 2;
                matrix.postTranslate(0, translateY);
//                LogUtils.i("transY %f , %f", scalePreY, translateY);
            } else {//屏幕显示高宽尺寸比例较小的,即X轴偏移
                float scalePreX = sh / preScale; //preWidth * (sHeight / preHeight);
                //x轴需要放大的比例
                matrix.preScale(scalePreX / sw, 1.0f);
                float translateX = (sw - scalePreX) / 2;
                matrix.postTranslate(translateX, 0);
//                LogUtils.i("transX %f , %f", scalePreX, translateX);
            }
        }
        return matrix;
    }
}
