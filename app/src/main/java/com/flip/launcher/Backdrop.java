package com.flip.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * The wallpaper, decoded once at screen size (plus a little extra width for
 * parallax) together with a blurred copy. Every "glass" surface (dock,
 * search) just draws a slice of the blurred copy, so there is no live blur
 * and almost no per-frame cost. That is what keeps it smooth on old hardware.
 */
public final class Backdrop {
    public Bitmap sharp;
    public Bitmap blur;
    public int w, h, pad;

    private final RectF dst = new RectF();

    public static Backdrop load(Context ctx, int w, int h) {
        Backdrop b = new Backdrop();
        b.w = w;
        b.h = h;
        b.pad = w / 6;
        int tw = w + b.pad;

        Bitmap out = Bitmap.createBitmap(tw, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        boolean drawn = false;

        try {
            Drawable dr = WallpaperManager.getInstance(ctx).getDrawable();
            if (dr instanceof BitmapDrawable) {
                Bitmap bm = ((BitmapDrawable) dr).getBitmap();
                if (bm != null && bm.getWidth() > 0 && bm.getHeight() > 0) {
                    float s = Math.max(tw / (float) bm.getWidth(), h / (float) bm.getHeight());
                    Bitmap use = bm;
                    boolean temp = false;
                    if (s < 0.6f) {
                        // Pre-shrink big wallpapers so they don't alias.
                        use = Bitmap.createScaledBitmap(bm,
                                Math.max(1, (int) (bm.getWidth() * s)),
                                Math.max(1, (int) (bm.getHeight() * s)), true);
                        temp = true;
                        s = 1f;
                    }
                    float dx = (tw - use.getWidth() * s) / 2f;
                    float dy = (h - use.getHeight() * s) / 2f;
                    c.save();
                    c.translate(dx, dy);
                    c.scale(s, s);
                    c.drawBitmap(use, 0, 0, p);
                    c.restore();
                    if (temp && use != bm) use.recycle();
                    drawn = true;
                }
            }
        } catch (Throwable ignored) {
            // live wallpaper, out of memory, etc.: use the fallback below
        }

        if (!drawn) {
            // iOS 18-ish default: warm sand gradient with dark satin ribbons.
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new LinearGradient(0, 0, tw * 0.6f, h, 0xFFEADFCF, 0xFF7C7266, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, tw, h, g);
            g.setShader(new RadialGradient(tw * 0.25f, h * 0.2f, Math.max(tw, h) * 0.6f,
                    0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, tw, h, g);
            g.setShader(null);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeCap(Paint.Cap.ROUND);
            Path ribbon = new Path();
            g.setStrokeWidth(h * 0.16f);
            g.setColor(0x553A342D);
            ribbon.moveTo(-tw * 0.1f, h * 0.55f);
            ribbon.cubicTo(tw * 0.3f, h * 0.15f, tw * 0.55f, h * 0.95f, tw * 1.1f, h * 0.35f);
            c.drawPath(ribbon, g);
            ribbon.reset();
            g.setStrokeWidth(h * 0.10f);
            g.setColor(0x44FFF6E8);
            ribbon.moveTo(-tw * 0.1f, h * 0.80f);
            ribbon.cubicTo(tw * 0.25f, h * 0.45f, tw * 0.6f, h * 1.1f, tw * 1.1f, h * 0.62f);
            c.drawPath(ribbon, g);
        }
        b.sharp = out;

        Bitmap small = Bitmap.createScaledBitmap(out, Math.max(8, tw / 4), Math.max(8, h / 4), true);
        b.blur = StackBlur.blur(small, 6);
        if (small != out) small.recycle();
        return b;
    }

    /** Draws the (blurred) wallpaper as seen from a view at (offX, offY) inside the anchor. */
    public void draw(Canvas c, boolean blurred, int offX, int offY, float frac, Paint paint) {
        Bitmap bm = (blurred && blur != null) ? blur : sharp;
        if (bm == null) return;
        float left = -offX - pad * frac;
        dst.set(left, -offY, left + w + pad, -offY + h);
        c.drawBitmap(bm, null, dst, paint);
    }
}
