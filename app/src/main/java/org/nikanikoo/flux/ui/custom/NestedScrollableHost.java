package org.nikanikoo.flux.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.viewpager2.widget.ViewPager2;

/**
 * Layout that wraps a scrollable component (e.g. horizontal RecyclerView) that lives inside
 * a ViewPager2. The host intercepts horizontal touch events and, if the child already started
 * scrolling horizontally, disallows the parent ViewPager2 from intercepting them.
 *
 * Based on the official Google ViewPager2 sample:
 * https://github.com/android/views-widgets-samples/blob/main/ViewPager2/app/src/main/java/androidx/viewpager2/integration/testapp/NestedScrollableHost.kt
 */
public class NestedScrollableHost extends FrameLayout {

    private int touchSlop;
    private float initialX = 0f;
    private float initialY = 0f;

    public NestedScrollableHost(Context context) {
        super(context);
        init(context);
    }

    public NestedScrollableHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NestedScrollableHost(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    private ViewPager2 getParentViewPager() {
        View v = (View) getParent();
        while (v != null && !(v instanceof ViewPager2)) {
            v = (View) v.getParent();
        }
        return (ViewPager2) v;
    }

    private View getChild() {
        return getChildCount() > 0 ? getChildAt(0) : null;
    }

    private boolean canChildScroll(int orientation, float delta) {
        int direction = (int) -Math.signum(delta);
        View child = getChild();
        if (child == null) return false;
        if (orientation == 0) {
            return child.canScrollHorizontally(direction);
        } else {
            return child.canScrollVertically(direction);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleInterceptTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    private void handleInterceptTouchEvent(MotionEvent e) {
        ViewPager2 parentViewPager = getParentViewPager();
        if (parentViewPager == null) return;

        int orientation = parentViewPager.getOrientation();

        // Early return if child can't scroll in same direction as parent
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f)) {
            return;
        }

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX();
            initialY = e.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = e.getX() - initialX;
            float dy = e.getY() - initialY;
            boolean isVpHorizontal = (orientation == ViewPager2.ORIENTATION_HORIZONTAL);

            // Check if user has moved past touch slop
            float scaledDx = Math.abs(dx) * (isVpHorizontal ? 0.5f : 1f);
            float scaledDy = Math.abs(dy) * (isVpHorizontal ? 1f : 0.5f);

            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isVpHorizontal == (scaledDy > scaledDx)) {
                    // Gesture is opposite to ViewPager orientation — let parent handle it
                    getParent().requestDisallowInterceptTouchEvent(false);
                } else {
                    // Child is scrolling in same direction as parent pager — block pager
                    if (canChildScroll(orientation, isVpHorizontal ? dx : dy)) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
            }
        }
    }
}
