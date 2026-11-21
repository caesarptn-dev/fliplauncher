package com.flip.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * One home-screen page: a fixed cols x rows grid of icons. Page one can carry
 * a header (the widgets) that takes the top rows; icons flow in below it.
 */
public class IconPage extends ViewGroup {
    private int cols = 4;
    private int rows = 4;
    private int headerRows = 0;
    private View header;

    public IconPage(Context c) {
        super(c);
    }

    public void setGrid(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
    }

    public void setHeader(View v, int rowSpan) {
        header = v;
        headerRows = rowSpan;
        addView(v, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int cw = w / cols;
        int ch = h / rows;
        int cws = MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY);
        int chs = MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == header) {
                v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(ch * headerRows, MeasureSpec.EXACTLY));
            } else {
                v.measure(cws, chs);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        int cw = w / cols;
        int ch = h / rows;
        int xPad = (w - cw * cols) / 2;
        int slot = headerRows * cols;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v == header) {
                v.layout(0, 0, w, ch * headerRows);
                continue;
            }
            int col = slot % cols;
            int row = slot / cols;
            int left = xPad + col * cw;
            int top = row * ch;
            v.layout(left, top, left + cw, top + ch);
            slot++;
        }
    }
}
