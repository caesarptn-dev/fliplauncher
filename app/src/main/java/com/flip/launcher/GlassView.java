package com.flip.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Draws a slice of the Backdrop. Used for the full-screen wallpaper
 * (unblurred) and for frosted-glass surfaces (blurred + tint). With a corner
 * radius it becomes the floating, rounded iOS dock / search panel.
 */
public class GlassView extends View {
    private Backdrop backdrop;
    private View anchor;
    private float frac;
    private boolean useBlur;
    private boolean topLine;
    private int tint;
    private float radius;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rf = new RectF();
    private final int[] a = new int[2];
    private final int[] m = new int[2];

    public GlassView(Context c) {
        super(c);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);
        border.setColor(0x66FFFFFF);
    }

    public void setBackdrop(Backdrop b, View anchorView) {
        backdrop = b;
        anchor = anchorView;
        invalidate();
    }

    public void setStyle(boolean blurred, int tintColor, boolean highlightLine) {
        useBlur = blurred;
        tint = tintColor;
        topLine = highlightLine;
        invalidate();
    }

    /** Corner radius in pixels; 0 = square. */
    public void setRadius(float px) {
        radius = px;
        rebuildClip();
        invalidate();
    }

    public void setFrac(float f) {
        if (f != frac) {
            frac = f;
            invalidate();
        }
    }

    private void rebuildClip() {
        clip.reset();
        if (radius > 0 && getWidth() > 0) {
            rf.set(0, 0, getWidth(), getHeight());
            clip.addRoundRect(rf, radius, radius, Path.Direction.CW);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        rebuildClip();
    }

    @Override
    protected void onDraw(Canvas c) {
        int offX = 0, offY = 0;
        if (anchor != null && anchor != this) {
            anchor.getLocationInWindow(a);
            getLocationInWindow(m);
            offX = m[0] - a[0];
            offY = m[1] - a[1];
        }
        boolean round = radius > 0;
        if (round) {
            c.save();
            c.clipPath(clip);
        }
        if (backdrop != null) {
            backdrop.draw(c, useBlur, offX, offY, frac, bmpPaint);
        }
        if ((tint >>> 24) != 0) {
            fill.setColor(tint);
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
        }
        if (topLine && !round) {
            fill.setColor(0x88FFFFFF);
            c.drawRect(0, 0, getWidth(), 1, fill);
        }
        if (round) {
            c.restore();
            rf.set(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f);
            c.drawRoundRect(rf, radius, radius, border);
        }
    }
}
