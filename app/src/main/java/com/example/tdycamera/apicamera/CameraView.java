package com.example.tdycamera.apicamera;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.example.tdycamera.R;
import com.example.tdycamera.api.Camera1;
import com.example.tdycamera.api.Camera2;
import com.example.tdycamera.api.Camera2Api23;
import com.example.tdycamera.base.AspectRatio;
import com.example.tdycamera.base.CameraViewImpl;
import com.example.tdycamera.base.Constants;
import com.example.tdycamera.base.PreviewImpl;
import com.example.tdycamera.view.DisplayOrientationDetector;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Set;

public class CameraView extends FrameLayout {
    private String TAG = getClass().getSimpleName();
    //相机设备与设备屏幕的方向相反.
    public static final int FACING_BACK = Constants.FACING_BACK;

    // 相机设备与设备屏幕的方向相同.
    public static final int FACING_FRONT = Constants.FACING_FRONT;

    // 相机相对于设备屏幕的方向.
    @IntDef({FACING_BACK, FACING_FRONT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
    }

    //闪光灯关闭
    public static final int FLASH_OFF = Constants.FLASH_OFF;

    //闪光灯开启
    public static final int FLASH_ON = Constants.FLASH_ON;

    //在预览、自动对焦和快照十开启
    public static final int FLASH_TORCH = Constants.FLASH_TORCH;

    //闪光灯将在需要时自动启动
    public static final int FLASH_AUTO = Constants.FLASH_AUTO;

    //闪光灯在red_eye模式下触发
    public static final int FLASH_RED_EYE = Constants.FLASH_RED_EYE;

    //照相机设备闪光灯控制的模式
    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    public @interface Flash {
    }
    //相机抽象类
    private CameraViewImpl mImpl;

    private final CallbackBridge mCallbacks;

    //是否正在调整视图边界
    private boolean mAdjustViewBounds;

    //屏幕旋转监听
    private final DisplayOrientationDetector mDisplayOrientationDetector;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            mCallbacks = null;
            mDisplayOrientationDetector = null;
            return;
        }
        //预览控件抽象类
        final PreviewImpl preview = createPreviewImpl(context);
        mCallbacks = new CallbackBridge();
        if (Build.VERSION.SDK_INT < 21) {
            mImpl = new Camera1(mCallbacks, preview);
        } else if (Build.VERSION.SDK_INT < 23) {
            mImpl = new Camera2(mCallbacks, preview, context);
        } else {
            mImpl = new Camera2Api23(mCallbacks, preview, context);
        }
        // 读取设置的默认属性
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView, defStyleAttr,
                R.style.Widget_CameraView);
        //是否调整边界
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
        //默认前置
        setFacing(a.getInt(R.styleable.CameraView_facing, CameraView.FACING_FRONT));
        //设置宽高比默认4:3
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (aspectRatio != null) {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        } else {
            setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        //设置是否自动对焦
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));
        //设置闪光灯模式
        setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        Log.e(TAG,"是否自动调整边界 "+ mAdjustViewBounds +
                " 是否前置 "+a.getInt(R.styleable.CameraView_facing, CameraView.FACING_FRONT)+
                " 默认宽高比 "+aspectRatio+
                " 是否自动对焦 "+a.getBoolean(R.styleable.CameraView_autoFocus, true)+
                "闪关灯模式 "+a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        a.recycle();
        // 设置显示方向
        mDisplayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                mImpl.setDisplayOrientation(displayOrientation);
            }
        };

    }

    @NonNull
    private PreviewImpl createPreviewImpl(Context context) {
        PreviewImpl preview;
        if (Build.VERSION.SDK_INT >= 23) {
            preview = new SurfaceViewPreview(context, this);
        } else {
            preview = new TextureViewPreview(context, this);
        }
        return preview;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        //进行窗口尺寸的修改
        if (!isInEditMode()) {
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // 调整视图边界
        if (mAdjustViewBounds) {
            if (!isCameraOpened()) {
                mCallbacks.reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int height = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int width = (int) (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        // 测量视图
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if (mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) {
            ratio = ratio.inverse();
        }
        assert ratio != null;
        if (height < width * ratio.getY() / ratio.getX()) {
            mImpl.getView().measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                            MeasureSpec.EXACTLY));
        } else {
            mImpl.getView().measure(
                    MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(),
                            MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.facing = getFacing();
        state.ratio = getAspectRatio();
        state.autoFocus = getAutoFocus();
        state.flash = getFlash();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setFacing(ss.facing);
        setAspectRatio(ss.ratio);
        setAutoFocus(ss.autoFocus);
        setFlash(ss.flash);
    }

    /**
     * 打开相机设备并开始显示相机预览。这通常在Activity的onResume调用
     */
    public void start() {
        if (!mImpl.start()) {
            //存储状态，并在回滚到Camera1后恢复此状态
            Parcelable state = onSaveInstanceState();
            // Camera2使用传统硬件层；回到摄影机1
            mImpl = new Camera1(mCallbacks, createPreviewImpl(getContext()));
            onRestoreInstanceState(state);
            mImpl.start();
        }
    }

    /**
     * 停止相机预览并关闭设备。这通常在Activity的onPause调用
     */
    public void stop() {
        mImpl.stop();
    }

    /**
     * 相机是否被打开
     */
    public boolean isCameraOpened() {
        return mImpl.isCameraOpened();
    }

    /**
     * Add a new callback.
     */
    public void addCallback(@NonNull Callback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Remove a callback.
     */
    public void removeCallback(@NonNull Callback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * 调整摄影机视图边界以保留摄影机的纵横比。
     */
    public void setAdjustViewBounds(boolean adjustViewBounds) {
        if (mAdjustViewBounds != adjustViewBounds) {
            mAdjustViewBounds = adjustViewBounds;
            requestLayout();
        }
    }

    /**
     * 摄影机视图是否正在调整其边界以保持摄影机的纵横比
     */
    public boolean getAdjustViewBounds() {
        return mAdjustViewBounds;
    }

    /**
     * 根据摄影机面对的方向选择摄影机。
     */
    public void setFacing(@Facing int facing) {
        mImpl.setFacing(facing);
    }

    /**
     * 获取当前摄影机面对的方向。
     */
    @Facing
    public int getFacing() {
        return mImpl.getFacing();
    }

    /**
     * 获取当前相机支持的所有纵横比。
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mImpl.getSupportedAspectRatios();
    }

    /**
     * 设置相机的纵横比。
     */
    public void setAspectRatio(@NonNull AspectRatio ratio) {
        if (mImpl.setAspectRatio(ratio)) {
            requestLayout();
        }
    }

    /**
     * 获取摄影机的当前纵横比。没有相机打开的话会返回空
     */
    @Nullable
    public AspectRatio getAspectRatio() {
        return mImpl.getAspectRatio();
    }

    /**
     * 启用或禁用连续自动对焦模式。当当前相机不支持自动对焦时，调用此方法将被忽略。
     */
    public void setAutoFocus(boolean autoFocus) {
        mImpl.setAutoFocus(autoFocus);
    }

    /**
     * 返回是否启用连续自动对焦模式。
     */
    public boolean getAutoFocus() {
        return mImpl.getAutoFocus();
    }

    /**
     * 设置闪光模式.
     */
    public void setFlash(@Flash int flash) {
        mImpl.setFlash(flash);
    }

    /**
     * 获取当前的闪光模式。
     */
    @Flash
    public int getFlash() {
        return mImpl.getFlash();
    }

    /**
     * 拍照，结果通过Callback的onPictureTaken返回
     */
    public void takePicture() {
        mImpl.takePicture();
    }

    private class CallbackBridge implements CameraViewImpl.Callback {

        private final ArrayList<Callback> mCallbacks = new ArrayList<>();

        private boolean mRequestLayoutOnOpen;

        CallbackBridge() {
        }

        public void add(Callback callback) {
            mCallbacks.add(callback);
        }

        public void remove(Callback callback) {
            mCallbacks.remove(callback);
        }

        @Override
        public void onCameraOpened() {
            if (mRequestLayoutOnOpen) {
                mRequestLayoutOnOpen = false;
                requestLayout();
            }
            for (Callback callback : mCallbacks) {
                callback.onCameraOpened(CameraView.this);
            }
        }

        @Override
        public void onCameraClosed() {
            for (Callback callback : mCallbacks) {
                callback.onCameraClosed(CameraView.this);
            }
        }

        @Override
        public void onPictureTaken(byte[] data) {
            for (Callback callback : mCallbacks) {
                callback.onPictureTaken(CameraView.this, data);
            }
        }

        public void reserveRequestLayoutOnOpen() {
            mRequestLayoutOnOpen = true;
        }
    }

    protected static class SavedState extends BaseSavedState {

        @Facing
        int facing;

        AspectRatio ratio;

        boolean autoFocus;

        @Flash
        int flash;

        @SuppressWarnings("WrongConstant")
        public SavedState(Parcel source, ClassLoader loader) {
            super(source);
            facing = source.readInt();
            ratio = source.readParcelable(loader);
            autoFocus = source.readByte() != 0;
            flash = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeParcelable(ratio, 0);
            out.writeByte((byte) (autoFocus ? 1 : 0));
            out.writeInt(flash);
        }

        public static final Creator<SavedState> CREATOR
                = new ClassLoaderCreator<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in) {
                return createFromParcel(in, null);
            }

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }

        };

    }

    /**
     *  预览事件的回调
     */
    @SuppressWarnings("UnusedParameters")
    public abstract static class Callback {

        /**
         * 打开相机时调用。
         */
        public void onCameraOpened(CameraView cameraView) {
        }

        /**
         * 相机关闭时调用。
         */
        public void onCameraClosed(CameraView cameraView) {
        }

        /**
         * 拍照时调用。JPEG data.
         */
        public void onPictureTaken(CameraView cameraView, byte[] data) {
        }
    }

}
