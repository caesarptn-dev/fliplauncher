package com.flip.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Spotlight-style search over the frosted wallpaper. Opens automatically when
 * you type on the phone's hardware keyboard; Enter launches the top result.
 */
public class SpotlightView extends FrameLayout {

    public interface Listener {
        void onPick(AppInfo app);
    }

    private final GlassView bg;
    private final EditText input;
    private final ListView list;
    private final float d;
    private final ArrayList<AppInfo> all = new ArrayList<AppInfo>();
    private final ArrayList<AppInfo> shown = new ArrayList<AppInfo>();
    private final Adapter adapter = new Adapter();
    private Listener listener;

    public SpotlightView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);

        bg = new GlassView(c);
        addView(bg, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(8), dp(8), dp(8), 0);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        input = new EditText(c);
        input.setSingleLine(true);
        input.setHint("Search");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x99FFFFFF);
        input.setTextSize(15);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x40000000);
        pill.setCornerRadius(dp(16));
        pill.setStroke(1, 0x55FFFFFF);
        input.setBackgroundDrawable(pill);
        input.setPadding(dp(12), dp(6), dp(12), dp(6));
        col.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        list = new ListView(c);
        list.setDivider(null);
        list.setCacheColorHint(0);
        list.setSelector(new ColorDrawable(0x33FFFFFF));
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                pick(position);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.topMargin = dp(6);
        col.addView(list, lp);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) {}
            @Override public void afterTextChanged(Editable e) {
                filter(e.toString());
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    pick(0);
                    return true;
                }
                return false;
            }
        });

        setVisibility(GONE);
    }

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setBackdrop(Backdrop b, View anchor) {
        bg.setBackdrop(b, anchor);
    }

    public void setStyle(boolean blurred, int tint) {
        bg.setStyle(blurred, tint, false);
    }

    public void setApps(ArrayList<AppInfo> apps) {
        all.clear();
        all.addAll(apps);
        filter(input.getText().toString());
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public void open(String initial) {
        setVisibility(VISIBLE);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(150);
        startAnimation(fade);
        input.setText(initial == null ? "" : initial);
        input.setSelection(input.getText().length());
        input.requestFocus();
        Configuration cfg = getResources().getConfiguration();
        boolean hwKeys = cfg.keyboard != Configuration.KEYBOARD_NOKEYS
                && cfg.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO;
        if (!hwKeys) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void close() {
        if (!isOpen()) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        clearAnimation();
        setVisibility(GONE);
        input.setText("");
    }

    private void pick(int pos) {
        if (pos >= 0 && pos < shown.size() && listener != null) listener.onPick(shown.get(pos));
    }

    private void filter(String raw) {
        String q = raw.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        if (q.length() == 0) {
            shown.addAll(all);
        } else {
            for (AppInfo a : all) if (a.lower.startsWith(q)) shown.add(a);
            for (AppInfo a : all) if (!a.lower.startsWith(q) && a.lower.contains(q)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            ImageView iv;
            TextView tv;
            if (convert == null) {
                Context c = parent.getContext();
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(6), dp(4), dp(6), dp(4));
                iv = new ImageView(c);
                row.addView(iv, new LinearLayout.LayoutParams(dp(34), dp(34)));
                tv = new TextView(c);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(15);
                tv.setSingleLine(true);
                tv.setShadowLayer(dp(1.5f), 0, dp(1), 0xCC000000);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = dp(10);
                row.addView(tv, lp);
                row.setTag(new Object[]{iv, tv});
            } else {
                row = (LinearLayout) convert;
            }
            Object[] t = (Object[]) row.getTag();
            iv = (ImageView) t[0];
            tv = (TextView) t[1];
            AppInfo a = shown.get(i);
            iv.setImageBitmap(a.icon);
            tv.setText(a.label);
            return row;
        }
    }
}
