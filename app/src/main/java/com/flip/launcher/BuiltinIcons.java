package com.flip.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Hand-drawn iOS-style tiles for the four dock apps (Phone, Messages,
 * Browser, Camera). Drawn once with Canvas primitives, no image assets.
 */
public final class BuiltinIcons {
    public static final int PHONE = 0, MESSAGES = 1, BROWSER = 2, CAMERA = 3;

    private BuiltinIcons() {}

    public static Bitmap make(int kind, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = size;
        float r = s * 0.225f;
        RectF rect = new RectF(0, 0, s, s);

        switch (kind) {
            case PHONE:
                body(c, p, s, 0xFF6BE67F, 0xFF1FB53A);
                phone(c, p, s);
                break;
            case MESSAGES:
                body(c, p, s, 0xFF6BE67F, 0xFF1FB53A);
                bubble(c, p, s);
                break;
            case BROWSER:
                body(c, p, s, 0xFFFFFFFF, 0xFFDCE0E6);
                compass(c, p, s);
                break;
            default:
                body(c, p, s, 0xFFD3D6DC, 0xFF8C9098);
                camera(c, p, s);
                break;
        }

        // smooth rounded-corner mask
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawRoundRect(rect, r, r, p);
        p.setXfermode(null);

        // faint border
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x24000000);
        c.drawRoundRect(new RectF(0.5f, 0.5f, s - 0.5f, s - 0.5f), r, r, p);
        return out;
    }

    private static void body(Canvas c, Paint p, float s, int top, int bot) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, s, top, bot, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, s, s, p);
        p.setShader(null);
    }

    /** Material-style handset outline in a 24x24 box. */
    private static void phone(Canvas c, Paint p, float s) {
        Path h = new Path();
        h.moveTo(6.62f, 10.79f);
        h.rCubicTo(1.44f, 2.83f, 3.76f, 5.14f, 6.59f, 6.59f);
        h.rLineTo(2.2f, -2.2f);
        h.rCubicTo(0.27f, -0.27f, 0.67f, -0.36f, 1.02f, -0.24f);
        h.rCubicTo(1.12f, 0.37f, 2.33f, 0.57f, 3.57f, 0.57f);
        h.rCubicTo(0.55f, 0f, 1f, 0.45f, 1f, 1f);
        h.rLineTo(0f, 3.49f);
        h.rCubicTo(0f, 0.55f, -0.45f, 1f, -1f, 1f);
        h.rCubicTo(-9.39f, 0f, -17f, -7.61f, -17f, -17f);
        h.rCubicTo(0f, -0.55f, 0.45f, -1f, 1f, -1f);
        h.rLineTo(3.5f, 0f);
        h.rCubicTo(0.55f, 0f, 1f, 0.45f, 1f, 1f);
        h.rCubicTo(0f, 1.25f, 0.2f, 2.45f, 0.57f, 3.57f);
        h.rCubicTo(0.11f, 0.35f, 0.03f, 0.74f, -0.25f, 1.02f);
        h.rLineTo(-2.2f, 2.2f);
        h.close();

        float k = s * 0.60f / 24f;
        float off = (s - 24f * k) / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.save();
        c.translate(off, off);
        c.scale(k, k);
        c.drawPath(h, p);
        c.restore();
    }

    private static void bubble(Canvas c, Paint p, float s) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        c.drawOval(new RectF(0.16f * s, 0.20f * s, 0.84f * s, 0.69f * s), p);
        Path t = new Path();
        t.moveTo(0.27f * s, 0.58f * s);
        t.lineTo(0.19f * s, 0.81f * s);
        t.lineTo(0.43f * s, 0.67f * s);
        t.close();
        c.drawPath(t, p);
    }

    private static void compass(Canvas c, Paint p, float s) {
        float cx = s / 2f, cy = s / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, cy - 0.37f * s, 0, cy + 0.37f * s,
                0xFF67C5FF, 0xFF0A62E4, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 0.37f * s, p);
        p.setShader(null);

        // tick marks
        p.setColor(0xCCFFFFFF);
        p.setStrokeWidth(Math.max(1f, s * 0.012f));
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            float x = (float) Math.sin(a), y = (float) -Math.cos(a);
            float r1 = 0.33f * s, r0 = (i % 3 == 0 ? 0.29f : 0.31f) * s;
            c.drawLine(cx + x * r0, cy + y * r0, cx + x * r1, cy + y * r1, p);
        }

        c.save();
        c.rotate(45, cx, cy);
        Path red = new Path();
        red.moveTo(cx, cy - 0.27f * s);
        red.lineTo(cx + 0.07f * s, cy);
        red.lineTo(cx - 0.07f * s, cy);
        red.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF3B30);
        c.drawPath(red, p);
        Path white = new Path();
        white.moveTo(cx, cy + 0.27f * s);
        white.lineTo(cx + 0.07f * s, cy);
        white.lineTo(cx - 0.07f * s, cy);
        white.close();
        p.setColor(0xFFF2F2F7);
        c.drawPath(white, p);
        c.restore();
    }

    private static void camera(Canvas c, Paint p, float s) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1C1C1E);
        c.drawRoundRect(new RectF(0.14f * s, 0.30f * s, 0.86f * s, 0.74f * s), 0.09f * s, 0.09f * s, p);
        c.drawRoundRect(new RectF(0.56f * s, 0.23f * s, 0.78f * s, 0.34f * s), 0.03f * s, 0.03f * s, p);
        p.setColor(0xFF48484A);
        c.drawCircle(0.5f * s, 0.52f * s, 0.17f * s, p);
        p.setColor(0xFF08080A);
        c.drawCircle(0.5f * s, 0.52f * s, 0.125f * s, p);
        p.setColor(0x99FFFFFF);
        c.drawCircle(0.46f * s, 0.48f * s, 0.03f * s, p);
        p.setColor(0xFFFFD60A);
        c.drawCircle(0.75f * s, 0.39f * s, 0.018f * s, p);
    }
}
