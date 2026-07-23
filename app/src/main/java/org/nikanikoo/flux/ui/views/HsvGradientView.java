package org.nikanikoo.flux.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class HsvGradientView extends View {
    private Paint paint;
    private Paint indicatorPaint;
    private float hue = 0f; // 0-360
    private float saturation = 1f; // 0-1
    private float value = 1f; // 0-1
    private OnColorChangedListener listener;

    public interface OnColorChangedListener {
        void onColorChanged(float h, float s, float v);
    }

    public HsvGradientView(Context context) {
        super(context);
        init();
    }

    public HsvGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HsvGradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
    }

    public void setHue(float hue) {
        this.hue = hue;
        invalidate();
        if (listener != null) {
            listener.onColorChanged(hue, saturation, value);
        }
    }

    public void setColor(float h, float s, float v) {
        this.hue = h;
        this.saturation = s;
        this.value = v;
        invalidate();
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    public float getHue() {
        return hue;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getValue() {
        return value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        int pureHueColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        Shader satShader = new LinearGradient(0, 0, w, 0, Color.WHITE, pureHueColor, Shader.TileMode.CLAMP);
        Shader valShader = new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);

        ComposeShader composeShader = new ComposeShader(satShader, valShader, PorterDuff.Mode.SRC_OVER);
        paint.setShader(composeShader);
        canvas.drawRect(0, 0, w, h, paint);

        float cx = saturation * w;
        float cy = (1f - value) * h;
        
        indicatorPaint.setColor(value > 0.5f && saturation < 0.5f ? Color.BLACK : Color.WHITE);
        canvas.drawCircle(cx, cy, 8f * getResources().getDisplayMetrics().density, indicatorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                float y = Math.max(0, Math.min(event.getY(), getHeight()));

                saturation = x / getWidth();
                value = 1f - (y / getHeight());

                invalidate();
                if (listener != null) {
                    listener.onColorChanged(hue, saturation, value);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
