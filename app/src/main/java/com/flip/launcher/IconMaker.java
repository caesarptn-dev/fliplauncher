package com.flip.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Turns any Android icon into an iOS tile.
 *  - gloss == false (default): modern flat iOS 7-18 look: tinted gradient
 *    tile, glyph on top, hairline border.
 *  - gloss == true: iOS 6 look: glyph shadow, darkened bottom edge, glossy
 *    top highlight, inner bevel.
 * Rendered once per app and cached, never at draw time.
 */
public final class IconMaker {
    private IconMaker() {}

    public static Bitmap make(Drawable d, int size, boolean gloss) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        int g = (int) (size * (gloss ? 0.72f : 0.76f));
        Bitmap glyph = Bitmap.createBitmap(g, g, Bitmap.Config.ARGB_8888);
        Canvas gc = new Canvas(glyph);
        d.setBounds(0, 0, g, g);
        d.draw(gc);

        int avg = average(glyph);
        int top, bot;
        float[] hsv = new float[3];
        Color.colorToHSV(avg, hsv);
        if (hsv[1] < 0.14f) {
            // white / grey glyphs get a clean white tile, like Notes or Calendar
            top = Color.rgb(255, 255, 255);
            bot = Color.rgb(gloss ? 190 : 226, gloss ? 195 : 229, gloss ? 206 : 235);
        } else if (gloss) {
            top = Color.HSVToColor(new float[]{hsv[0], Math.min(hsv[1], 0.55f) * 0.70f, 1f});
            bot = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.95f + 0.12f), 0.60f});
        } else {
            top = Color.HSVToColor(new float[]{hsv[0], Math.min(hsv[1], 0.6f) * 0.55f, 1f});
            bot = Color.HSVToColor(new float[]{hsv[0], Math.min(1f, hsv[1] * 0.75f + 0.10f), 0.93f});
        }

        float r = size * 0.225f;
        RectF rect = new RectF(0, 0, size, size);

        p.setShader(new LinearGradient(0, 0, 0, size, top, bot, Shader.TileMode.CLAMP));
        c.drawRoundRect(rect, r, r, p);
        p.setShader(null);

        float off = (size - g) / 2f;
        if (gloss) {
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            sp.setColorFilter(new PorterDuffColorFilter(0x66000000, PorterDuff.Mode.SRC_IN));
            c.drawBitmap(glyph, off, off + Math.max(1f, size * 0.03f), sp);
        }
        c.drawBitmap(glyph, off, off, p);

        if (gloss) {
            p.setShader(new LinearGradient(0, size * 0.62f, 0, size, 0x00000000, 0x38000000, Shader.TileMode.CLAMP));
            c.drawRect(0, size * 0.62f, size, size, p);
            p.setShader(null);

            RectF gl = new RectF(-size * 0.25f, -size * 0.95f, size * 1.25f, size * 0.50f);
            p.setShader(new LinearGradient(0, 0, 0, size * 0.5f, 0x8CFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
            c.drawOval(gl, p);
            p.setShader(null);
        }

        // Round off anything that spilled outside the tile.
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        p.setColor(Color.WHITE);
        c.drawRoundRect(rect, r, r, p);
        p.setXfermode(null);

        p.setStyle(Paint.Style.STROKE);
        if (gloss) {
            float sw = Math.max(1f, size * 0.03f);
            float in = sw / 2f + 0.5f;
            p.setStrokeWidth(sw);
            p.setShader(new LinearGradient(0, 0, 0, size * 0.5f, 0xAAFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(in, in, size - in, size - in), r - in, r - in, p);
            p.setShader(null);
        }
        p.setStrokeWidth(1f);
        p.setColor(gloss ? 0x4A000000 : 0x24000000);
        c.drawRoundRect(new RectF(0.5f, 0.5f, size - 0.5f, size - 0.5f), r, r, p);

        glyph.recycle();
        return out;
    }

    /** Alpha-weighted average colour of the glyph. */
    private static int average(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        long r = 0, g = 0, b = 0, a = 0;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int al = c >>> 24;
            if (al < 24) continue;
            r += ((c >> 16) & 0xFF) * al;
            g += ((c >> 8) & 0xFF) * al;
            b += (c & 0xFF) * al;
            a += al;
        }
        if (a == 0) return Color.rgb(160, 160, 170);
        return Color.rgb((int) (r / a), (int) (g / a), (int) (b / a));
    }
}
