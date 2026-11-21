package com.flip.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/**
 * The iOS 16+ "Search" pill that sits above the dock. While you are swiping
 * between pages it turns into the page dots, then back into the pill.
 * Tapping it opens search.
 */
public class PageDots extends View {
    private int count = 1;
    private float pos;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float d;

    public PageDots(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(Math.max(10f, d * 10.5f));
    }

    public void setCount(int n) {
        count = Math.max(1, n);
        invalidate();
    }

    public void setPosition(float page) {
        pos = page;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float swipe = Math.abs(pos - Math.round(pos));

        if (count > 1 && swipe > 0.03f) {
            float r = d * 2.6f;
            float gap = d * 11;
            float x0 = cx - (count - 1) * gap / 2f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < count; i++) {
                float dist = Math.min(1f, Math.abs(pos - i));
                int alpha = (int) (255 - 150 * dist);
                p.setColor((alpha << 24) | 0xFFFFFF);
                c.drawCircle(x0 + i * gap, cy, r, p);
            }
            return;
        }

        float pw = d * 78, ph = d * 20;
        rf.set(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(rf, ph / 2, ph / 2, p);

        String s = "Search";
        float tw = tp.measureText(s);
        float mag = d * 9;
        float start = cx - (mag + d * 4 + tw) / 2f;

        // magnifier
        float mx = start + d * 3.4f;
        float my = cy - d * 0.8f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d * 1.2f));
        p.setColor(0xFFFFFFFF);
        c.drawCircle(mx, my, d * 3f, p);
        c.drawLine(mx + d * 2.2f, my + d * 2.2f, mx + d * 4.6f, my + d * 4.6f, p);

        c.drawText(s, start + mag + d * 4, cy + tp.getTextSize() * 0.35f, tp);
    }
}
