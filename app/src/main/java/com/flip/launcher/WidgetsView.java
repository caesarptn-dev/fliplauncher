package com.flip.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.BatteryManager;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * The two big widgets at the top of page one, drawn by hand:
 *  - left : blue "weather-style" card showing time, date and battery
 *  - right: map-style card, tap to open Maps
 * No network, no permissions, redraws once a minute.
 */
public class WidgetsView extends View {

    public interface Listener {
        void onWidget(int which); // 0 = clock card, 1 = map card
    }

    private final float d;
    private final int slop;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF left = new RectF();
    private final RectF right = new RectF();
    private final RectF tmp = new RectF();
    private final Path clip = new Path();

    private Shader blueShader, glowShader, fadeShader;
    private String timeStr = "";
    private String dateStr = "";
    private int battery = -1;
    private boolean charging;
    private int pressed = -1;
    private float downX, downY;
    private boolean moved;
    private Listener listener;

    public WidgetsView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        refresh();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void refresh() {
        long now = System.currentTimeMillis();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        timeStr = DateFormat.format(h24 ? "HH:mm" : "h:mm", now).toString();
        dateStr = DateFormat.format("EEE, MMM d", now).toString();
        try {
            Intent b = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                battery = level < 0 ? -1 : level * 100 / Math.max(1, scale);
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING
                        || st == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception ignored) {
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float m = w * 0.04f;
        float g = w * 0.04f;
        float labelH = d * 15;
        float top = h * 0.04f;
        float cw = (w - 2 * m - g) / 2f;
        left.set(m, top, m + cw, h - labelH);
        right.set(m + cw + g, top, w - m, h - labelH);
        blueShader = new LinearGradient(0, left.top, 0, left.bottom, 0xFF5B95D6, 0xFF1D4C92, Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(left.left + cw * 0.2f, left.top, cw * 0.9f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        fadeShader = new LinearGradient(0, right.top + right.height() * 0.55f, 0, right.bottom,
                0x00FFFFFF, 0xE6FFFFFF, Shader.TileMode.CLAMP);
    }

    private int hit(float x, float y) {
        if (left.contains(x, y)) return 0;
        if (right.contains(x, y)) return 1;
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                moved = false;
                pressed = hit(downX, downY);
                invalidate();
                return pressed >= 0; // gaps fall through so the pager can long-press
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                    moved = true;
                    if (pressed != -1) {
                        pressed = -1;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                int which = pressed;
                pressed = -1;
                invalidate();
                if (!moved && which >= 0 && listener != null) listener.onWidget(which);
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (blueShader == null) return;
        drawClock(c);
        drawMap(c);
        label(c, left, "Clock");
        label(c, right, "Maps");
    }

    private void label(Canvas c, RectF card, String s) {
        tp.setShader(null);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(0xFFFFFFFF);
        tp.setFakeBoldText(false);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(Math.max(10f, d * 10.5f));
        tp.setShadowLayer(Math.max(1f, d * 1.2f), 0, Math.max(1f, d), 0xE6000000);
        c.drawText(s, card.centerX(), card.bottom + d * 11.5f, tp);
        tp.clearShadowLayer();
    }

    private void clipTo(Canvas c, RectF r) {
        float rad = Math.min(r.width(), r.height()) * 0.16f;
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
    }

    // ------------------------------------------------------------ clock card

    private void drawClock(Canvas c) {
        c.save();
        clipTo(c, left);
        p.setStyle(Paint.Style.FILL);
        p.setShader(blueShader);
        c.drawRect(left, p);
        p.setShader(glowShader);
        c.drawRect(left, p);
        p.setShader(null);

        float pad = d * 11;
        float x = left.left + pad;

        tp.setColor(0xFFFFFFFF);
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setFakeBoldText(true);
        tp.setTextSize(Math.max(10f, d * 11.5f));
        tp.setAlpha(230);
        c.drawText(dateStr, x, left.top + pad + tp.getTextSize() * 0.85f, tp);

        float size = Math.min(left.height() * 0.33f, left.width() * 0.30f);
        tp.setFakeBoldText(false);
        tp.setAlpha(255);
        tp.setTextSize(size);
        c.drawText(timeStr, x - d, left.top + pad + d * 14 + size * 0.95f, tp);

        // battery
        float bh = d * 9, bw = d * 20;
        float by = left.bottom - pad - bh;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d * 1.2f));
        p.setColor(0xCCFFFFFF);
        tmp.set(x, by, x + bw, by + bh);
        c.drawRoundRect(tmp, d * 2.2f, d * 2.2f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x + bw + d * 0.8f, by + bh * 0.3f, x + bw + d * 2.2f, by + bh * 0.7f, p);
        if (battery >= 0) {
            float fill = (bw - d * 3f) * battery / 100f;
            p.setColor(charging ? 0xFF6BE67F : (battery <= 15 ? 0xFFFF5A4F : 0xFFFFFFFF));
            tmp.set(x + d * 1.5f, by + d * 1.5f, x + d * 1.5f + Math.max(d, fill), by + bh - d * 1.5f);
            c.drawRoundRect(tmp, d, d, p);
        }
        tp.setTextSize(Math.max(10f, d * 10.5f));
        tp.setFakeBoldText(true);
        tp.setAlpha(230);
        String bt = battery >= 0 ? battery + "%" : "--";
        c.drawText(bt, x + bw + d * 6, by + bh - d * 0.5f, tp);
        tp.setAlpha(255);
        tp.setFakeBoldText(false);

        if (pressed == 0) {
            p.setColor(0x33000000);
            c.drawRect(left, p);
        }
        c.restore();
    }

    // -------------------------------------------------------------- map card

    private void drawMap(Canvas c) {
        c.save();
        clipTo(c, right);
        float x0 = right.left, y0 = right.top, w = right.width(), h = right.height();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF0EBE1);
        c.drawRect(right, p);

        // park + water
        p.setColor(0xFFCDE5C0);
        tmp.set(x0 + w * 0.06f, y0 + h * 0.52f, x0 + w * 0.46f, y0 + h * 0.78f);
        c.drawRect(tmp, p);
        p.setColor(0xFFB9D8F0);
        c.drawCircle(x0 + w * 0.95f, y0 + h * 0.08f, w * 0.3f, p);

        // roads
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        p.setStrokeWidth(d * 6);
        c.drawLine(x0, y0 + h * 0.38f, x0 + w, y0 + h * 0.14f, p);
        c.drawLine(x0 + w * 0.26f, y0, x0 + w * 0.56f, y0 + h, p);
        c.drawLine(x0, y0 + h * 0.84f, x0 + w, y0 + h * 0.62f, p);
        p.setStrokeWidth(d * 3);
        c.drawLine(x0 + w * 0.74f, y0, x0 + w * 0.92f, y0 + h, p);
        c.drawLine(x0, y0 + h * 0.6f, x0 + w * 0.5f, y0 + h * 0.5f, p);
        p.setColor(0xFFF6D571);
        p.setStrokeWidth(d * 4);
        c.drawLine(x0, y0 + h * 0.12f, x0 + w * 0.88f, y0 + h, p);
        p.setStrokeCap(Paint.Cap.BUTT);

        // text fade
        p.setStyle(Paint.Style.FILL);
        p.setShader(fadeShader);
        c.drawRect(right, p);
        p.setShader(null);

        // avatar pin
        float cx = x0 + w * 0.68f, cy = y0 + h * 0.30f;
        p.setColor(0x33000000);
        c.drawCircle(cx, cy + d * 1.5f, d * 13.5f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, d * 13, p);
        p.setShader(new LinearGradient(0, cy - d * 11, 0, cy + d * 11, 0xFF8FA8FF, 0xFF4C63D6, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, d * 11, p);
        p.setShader(null);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy - d * 2.8f, d * 3.6f, p);
        tmp.set(cx - d * 7f, cy + d * 1.2f, cx + d * 7f, cy + d * 14f);
        c.drawArc(tmp, 180, 180, true, p);

        // text
        float pad = d * 10;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setFakeBoldText(false);
        tp.setColor(0xFF6B6B70);
        tp.setTextSize(Math.max(9f, d * 9.5f));
        float base = right.bottom - pad;
        c.drawText("Tap to navigate", right.left + pad, base, tp);
        tp.setColor(0xFF111114);
        tp.setFakeBoldText(true);
        tp.setTextSize(Math.max(11f, d * 12.5f));
        c.drawText("Open Maps", right.left + pad, base - d * 13, tp);
        tp.setColor(0xFF6B6B70);
        tp.setFakeBoldText(false);
        tp.setTextSize(Math.max(9f, d * 9.5f));
        c.drawText("Now", right.left + pad, base - d * 27, tp);
        tp.setColor(0xFFFFFFFF);

        if (pressed == 1) {
            p.setColor(0x33000000);
            c.drawRect(right, p);
        }
        c.restore();
    }
}
