package org.nikanikoo.flux.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class HueSliderView extends View {
    private Paint paint;
    private Paint handlePaint;
    private float hue = 0f; // 0-360
    private OnHueChangedListener listener;

    public interface OnHueChangedListener {
        void onHueChanged(float hue);
    }

    public HueSliderView(Context context) {
        super(context);
        init();
    }

    public HueSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HueSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
    }

    public void setHue(float hue) {
        this.hue = hue;
        invalidate();
    }

    public void setOnHueChangedListener(OnHueChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();

        int[] colors = new int[7];
        colors[0] = Color.HSVToColor(new float[]{0f, 1f, 1f});
        colors[1] = Color.HSVToColor(new float[]{60f, 1f, 1f});
        colors[2] = Color.HSVToColor(new float[]{120f, 1f, 1f});
        colors[3] = Color.HSVToColor(new float[]{180f, 1f, 1f});
        colors[4] = Color.HSVToColor(new float[]{240f, 1f, 1f});
        colors[5] = Color.HSVToColor(new float[]{300f, 1f, 1f});
        colors[6] = Color.HSVToColor(new float[]{360f, 1f, 1f});

        Shader shader = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
        paint.setShader(shader);

        float density = getResources().getDisplayMetrics().density;
        float trackHeight = 12f * density;
        float top = (h - trackHeight) / 2f;
        float bottom = top + trackHeight;
        canvas.drawRoundRect(0, top, w, bottom, trackHeight / 2f, trackHeight / 2f, paint);

        float cx = (hue / 360f) * w;
        float cy = h / 2f;
        handlePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 10f * density, handlePaint);
        
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setColor(Color.HSVToColor(new float[]{hue, 1f, 1f}));
        canvas.drawCircle(cx, cy, 8f * density, innerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                hue = (x / getWidth()) * 360f;
                if (hue >= 360f) hue = 359.9f;

                invalidate();
                if (listener != null) {
                    listener.onHueChanged(hue);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }
}
