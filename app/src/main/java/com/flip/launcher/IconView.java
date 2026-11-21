package com.flip.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;

/**
 * One icon cell, drawn by hand (no child views, no layouts) so a page of
 * icons costs a handful of draw calls. Supports touch press (iOS-style dim),
 * d-pad focus, and the iOS "wiggle" edit mode with a black delete badge.
 */
public class IconView extends View {
    private AppInfo app;
    private boolean showLabel = true;
    private boolean editMode = false;
    private CharSequence label = "";

    private final float d;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final ColorFilter pressedFilter = new LightingColorFilter(0xFF9A9A9A, 0);

    private float iconL, iconT, iconS;
    private float downX, downY;

    public IconView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.max(10f, 10.5f * d));
        text.setShadowLayer(Math.max(1f, 1.2f * d), 0, Math.max(1f, d), 0xE6000000);
    }

    public void bind(AppInfo a, boolean withLabel) {
        app = a;
        showLabel = withLabel;
        updateGeometry();
        invalidate();
    }

    public AppInfo getApp() {
        return app;
    }

    public float getIconSize() {
        return iconS;
    }

    public void setEditMode(boolean on) {
        if (editMode == on) return;
        editMode = on;
        if (on) startWiggle(); else clearAnimation();
        invalidate();
    }

    /** True if the last touch landed on the delete badge. */
    public boolean hitBadge() {
        if (!editMode || app == null || !app.removable) return false;
        float bx = iconL + d * 2;
        float by = iconT + d * 2;
        float dx = downX - bx;
        float dy = downY - by;
        float r = d * 15;
        return dx * dx + dy * dy <= r * r;
    }

    private void startWiggle() {
        RotateAnimation a = new RotateAnimation(-2.4f, 2.4f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        a.setDuration(105 + (int) (Math.random() * 45));
        a.setRepeatMode(Animation.REVERSE);
        a.setRepeatCount(Animation.INFINITE);
        startAnimation(a);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updateGeometry();
    }

    private void updateGeometry() {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || app == null) return;
        float labelH = showLabel ? text.getTextSize() * 1.25f : 0;
        float maxIcon = Math.min(w * 0.82f, showLabel ? h - labelH - d * 6 : h * 0.9f);
        iconS = Math.min(maxIcon, d * 58);
        float block = iconS + (showLabel ? d * 3 + labelH : 0);
        iconT = (h - block) / 2f;
        iconL = (w - iconS) / 2f;
        label = TextUtils.ellipsize(app.label, text, w - d * 3, TextUtils.TruncateAt.END);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (app == null || app.icon == null) return;
        boolean pressed = isPressed();
        float corner = iconS * 0.225f;

        if (isFocused()) {
            rf.set(iconL - d * 3, iconT - d * 3, iconL + iconS + d * 3, iconT + iconS + d * 3);
            shape.setStyle(Paint.Style.FILL);
            shape.setColor(0x33FFFFFF);
            c.drawRoundRect(rf, corner + d * 3, corner + d * 3, shape);
            shape.setStyle(Paint.Style.STROKE);
            shape.setStrokeWidth(d * 1.5f);
            shape.setColor(0xCCFFFFFF);
            c.drawRoundRect(rf, corner + d * 3, corner + d * 3, shape);
        }

        // soft drop shadow under the tile (not while dimmed-pressed)
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0x4D000000);
        rf.set(iconL + d, iconT + d * 2.5f, iconL + iconS - d, iconT + iconS + d * 1.5f);
        c.drawRoundRect(rf, corner, corner, shape);

        // iOS dims the tile while pressed (no scaling)
        bmp.setColorFilter(pressed ? pressedFilter : null);
        rf.set(iconL, iconT, iconL + iconS, iconT + iconS);
        c.drawBitmap(app.icon, null, rf, bmp);

        if (showLabel) {
            float y = iconT + iconS + d * 3 - text.ascent();
            c.drawText(label, 0, label.length(), getWidth() / 2f, y, text);
        }

        if (editMode && app.removable) {
            float bx = iconL + d * 2;
            float by = iconT + d * 2;
            float br = d * 7.5f;
            shape.setStyle(Paint.Style.FILL);
            shape.setColor(0xF0101010);
            c.drawCircle(bx, by, br, shape);
            shape.setStyle(Paint.Style.STROKE);
            shape.setStrokeWidth(Math.max(1f, d * 1.3f));
            shape.setColor(0xFFFFFFFF);
            c.drawCircle(bx, by, br, shape);
            shape.setStrokeWidth(Math.max(1.5f, d * 1.8f));
            float k = d * 2.8f;
            c.drawLine(bx - k, by - k, bx + k, by + k, shape);
            c.drawLine(bx - k, by + k, bx + k, by - k, shape);
        }
    }
}
