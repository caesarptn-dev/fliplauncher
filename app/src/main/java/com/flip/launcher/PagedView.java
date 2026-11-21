package com.flip.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

/** Minimal horizontal pager with snap + fling (no support library needed). */
public class PagedView extends ViewGroup {

    public interface Listener {
        void onScrolled(float page);
        void onPageSelected(int page);
        void onEmptyLongPress();
    }

    private final Scroller scroller;
    private final int slop;
    private final int maxFling;
    private VelocityTracker vt;
    private float downX, downY, lastX;
    private boolean dragging;
    private boolean longFired;
    private int current;
    private Listener listener;

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (!dragging) {
                longFired = true;
                if (listener != null) listener.onEmptyLongPress();
            }
        }
    };

    public PagedView(Context c) {
        super(c);
        scroller = new Scroller(c, new DecelerateInterpolator(1.6f));
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        maxFling = vc.getScaledMaximumFlingVelocity();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int getCurrentPage() {
        return current;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int cws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int chs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(cws, chs);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scroller.abortAnimation();
        scrollTo(current * w, 0);
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (listener != null && getWidth() > 0) listener.onScrolled(l / (float) getWidth());
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidate();
        }
    }

    public void setPage(int page, boolean animate) {
        int n = Math.max(1, getChildCount());
        current = Math.max(0, Math.min(n - 1, page));
        if (animate) {
            snapToPage(current);
        } else {
            scroller.abortAnimation();
            scrollTo(current * getWidth(), 0);
        }
    }

    public void snapToPage(int page) {
        int n = Math.max(1, getChildCount());
        int target = Math.max(0, Math.min(n - 1, page));
        int w = getWidth();
        int dx = target * w - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 240);
        invalidate();
        if (target != current) {
            current = target;
            if (listener != null) listener.onPageSelected(current);
        }
    }

    private void track(MotionEvent ev) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(ev);
    }

    private void release() {
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    private void cancelLong() {
        removeCallbacks(longPress);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int a = ev.getAction() & MotionEvent.ACTION_MASK;
        if (a == MotionEvent.ACTION_MOVE && dragging) return true;
        float x = ev.getX();
        float y = ev.getY();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x;
                downY = y;
                dragging = !scroller.isFinished();
                if (dragging) scroller.abortAnimation();
                release();
                track(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                track(ev);
                float dx = Math.abs(x - downX);
                float dy = Math.abs(y - downY);
                if (dx > slop && dx > dy) {
                    dragging = true;
                    lastX = x;
                    cancelLong();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                release();
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int a = ev.getAction() & MotionEvent.ACTION_MASK;
        track(ev);
        float x = ev.getX();
        float y = ev.getY();
        int w = getWidth();
        int n = Math.max(1, getChildCount());
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = x;
                downY = y;
                longFired = false;
                cancelLong();
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(x - downX) > slop) {
                    dragging = true;
                    lastX = x;
                    cancelLong();
                }
                if (!dragging && Math.abs(y - downY) > slop) cancelLong();
                if (dragging) {
                    float nx = getScrollX() + (lastX - x);
                    lastX = x;
                    float min = -w / 6f;
                    float max = (n - 1) * w + w / 6f;
                    scrollTo((int) Math.max(min, Math.min(max, nx)), 0);
                }
                return true;
            case MotionEvent.ACTION_UP:
                cancelLong();
                if (dragging) {
                    vt.computeCurrentVelocity(1000, maxFling);
                    float vx = vt.getXVelocity();
                    int sx = getScrollX();
                    int base = Math.max(0, sx) / Math.max(1, w);
                    int target;
                    if (vx < -600) target = base + 1;
                    else if (vx > 600) target = base;
                    else target = (int) ((sx + w / 2f) / w);
                    snapToPage(target);
                }
                dragging = false;
                release();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelLong();
                if (dragging) snapToPage((int) ((getScrollX() + w / 2f) / Math.max(1, w)));
                dragging = false;
                release();
                return true;
            default:
                return true;
        }
    }

    /** Index of the page that contains this child view (or its ancestor page). */
    public int pageOf(View v) {
        View cur = v;
        while (cur != null && cur.getParent() != this) {
            if (!(cur.getParent() instanceof View)) return -1;
            cur = (View) cur.getParent();
        }
        return cur == null ? -1 : indexOfChild(cur);
    }
}
