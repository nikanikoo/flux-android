package org.nikanikoo.flux.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

public class CustomDrawerLayout extends DrawerLayout {

    private float mInitialTouchX;

    public CustomDrawerLayout(Context context) {
        super(context);
        initEdgeSize();
    }

    public CustomDrawerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initEdgeSize();
    }

    public CustomDrawerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initEdgeSize();
    }

    private void initEdgeSize() {
        try {
            java.lang.reflect.Field leftDraggerField = null;
            // 1. Try standard name
            try {
                leftDraggerField = DrawerLayout.class.getDeclaredField("mLeftDragger");
            } catch (NoSuchFieldException e) {
                // Fallback: search by type
                for (java.lang.reflect.Field field : DrawerLayout.class.getDeclaredFields()) {
                    if (field.getType() == androidx.customview.widget.ViewDragHelper.class) {
                        leftDraggerField = field;
                        break;
                    }
                }
            }

            if (leftDraggerField != null) {
                leftDraggerField.setAccessible(true);
                androidx.customview.widget.ViewDragHelper leftDragger = (androidx.customview.widget.ViewDragHelper) leftDraggerField.get(this);

                if (leftDragger != null) {
                    java.lang.reflect.Field edgeSizeField = null;
                    // 2. Try standard name for edge size
                    try {
                        edgeSizeField = androidx.customview.widget.ViewDragHelper.class.getDeclaredField("mEdgeSize");
                    } catch (NoSuchFieldException e) {
                        // Fallback: search for int field holding values close to standard default edge size (20dp in pixels)
                        int defaultEdgePixels = (int) (20 * getResources().getDisplayMetrics().density);
                        for (java.lang.reflect.Field field : androidx.customview.widget.ViewDragHelper.class.getDeclaredFields()) {
                            if (field.getType() == int.class) {
                                field.setAccessible(true);
                                int val = field.getInt(leftDragger);
                                if (val >= defaultEdgePixels - 5 && val <= defaultEdgePixels + 5) {
                                    edgeSizeField = field;
                                    break;
                                }
                            }
                        }
                    }

                    if (edgeSizeField != null) {
                        edgeSizeField.setAccessible(true);
                        int edgeSizeDp = 140;
                        int newEdgeSize = (int) (edgeSizeDp * getResources().getDisplayMetrics().density);
                        edgeSizeField.setInt(leftDragger, newEdgeSize);
                    }
                }
            }
        } catch (Exception e) {
            org.nikanikoo.flux.utils.Logger.e("CustomDrawerLayout", "Error setting custom drawer edge size", e);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Exclude the left edge zone (140dp) from system back gestures
            int exclusionWidth = (int) (140 * getResources().getDisplayMetrics().density);
            java.util.List<android.graphics.Rect> rects = new java.util.ArrayList<>();
            rects.add(new android.graphics.Rect(0, 0, exclusionWidth, getHeight()));
            setSystemGestureExclusionRects(rects);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mInitialTouchX = ev.getX();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        int edgeSizePx = (int) (140 * getResources().getDisplayMetrics().density);
        if (mInitialTouchX < edgeSizePx && !isDrawerOpen(GravityCompat.START)) {
            // Allow parent to intercept despite child scroll
            return;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            initEdgeSize();
        }
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            // Safe catch for potential multi-touch PointerIndexOutOfBoundsException inside DrawerLayout
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        try {
            return super.onTouchEvent(ev);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}