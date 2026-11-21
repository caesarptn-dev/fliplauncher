package com.flip.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * iOS 18-style home screen for Android 2.1+ (API 7), tuned for very small,
 * slow hardware with a slide-out keyboard (Motorola Flipout class).
 *
 * Performance rules used throughout:
 *  - icons are rendered to bitmaps once and cached
 *  - the wallpaper is blurred once, at 1/4 size, never live
 *  - no third-party libraries, no layout inflation, no nested layouts per icon
 *  - the activity handles keyboard-flip / rotation itself (no restart)
 *  - only API 7 calls (no ViewPropertyAnimator, no ActivityOptions, ...)
 */
public class LauncherActivity extends Activity {

    private static final HashMap<String, Bitmap> ICONS = new HashMap<String, Bitmap>();

    private SharedPreferences prefs;
    private float d;
    private boolean blur, parallax, gloss;

    private FrameLayout root;
    private GlassView backdropView;
    private GlassView dockGlass;
    private FrameLayout dockWrap;
    private LinearLayout dockRow;
    private PagedView pager;
    private PageDots dots;
    private WidgetsView widgets;
    private SpotlightView spotlight;
    private Backdrop backdrop;

    private final ArrayList<AppInfo> apps = new ArrayList<AppInfo>();
    private final ArrayList<AppInfo> dockApps = new ArrayList<AppInfo>();

    private boolean editMode;
    private boolean ticking;
    private int rootW = -1, rootH = -1, pagerW = -1, pagerH = -1;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (widgets != null) widgets.refresh();
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("flip", MODE_PRIVATE);
        blur = prefs.getBoolean("blur", true);
        parallax = prefs.getBoolean("parallax", true);
        gloss = prefs.getBoolean("gloss", false);

        buildUi();
        setContentView(root);
        applyStyle();
        applyOrientation();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                checkSizes();
            }
        });

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (widgets != null) widgets.refresh();
        if (!ticking) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            registerReceiver(tickReceiver, f);
            ticking = true;
        }
    }

    @Override
    protected void onPause() {
        if (ticking) {
            try {
                unregisterReceiver(tickReceiver);
            } catch (Exception ignored) {
            }
            ticking = false;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home button pressed: behave like iOS (close things, go to page 1).
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            spotlight.close();
            if (editMode) setEditMode(false);
            pager.snapToPage(0);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyOrientation();
    }

    @Override
    public void onBackPressed() {
        if (spotlight.isOpen()) spotlight.close();
        else if (editMode) setEditMode(false);
        else pager.snapToPage(0);
        // never call super: a launcher must not exit
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSettings();
            return true;
        }
        // Typing on the hardware keyboard opens Spotlight with that letter.
        if (!spotlight.isOpen() && !editMode && e.isPrintingKey() && !e.isAltPressed()) {
            int u = e.getUnicodeChar();
            if (u > 32) {
                spotlight.open(new String(Character.toChars(u)));
                return true;
            }
        }
        return super.onKeyDown(keyCode, e);
    }

    // ---------------------------------------------------------------- UI build

    private int dp(float v) {
        return (int) (v * d + 0.5f);
    }

    private void buildUi() {
        root = new FrameLayout(this);

        backdropView = new GlassView(this);
        root.addView(backdropView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pager = new PagedView(this);
        col.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dots = new PageDots(this);
        col.addView(dots, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        dots.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                spotlight.open("");
            }
        });

        // floating rounded dock
        dockWrap = new FrameLayout(this);
        dockGlass = new GlassView(this);
        dockGlass.setRadius(dp(24));
        dockWrap.addView(dockGlass, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setPadding(dp(6), dp(4), dp(6), dp(4));
        dockWrap.addView(dockRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66));
        dlp.setMargins(dp(10), dp(2), dp(10), dp(8));
        col.addView(dockWrap, dlp);

        spotlight = new SpotlightView(this);
        spotlight.setListener(new SpotlightView.Listener() {
            @Override
            public void onPick(AppInfo app) {
                spotlight.close();
                launch(app);
            }
        });
        root.addView(spotlight, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pager.setListener(new PagedView.Listener() {
            @Override
            public void onScrolled(float page) {
                dots.setPosition(page);
                int n = pager.getChildCount();
                float f = n > 1 ? Math.max(0f, Math.min(1f, page / (n - 1))) : 0f;
                if (parallax) {
                    backdropView.setFrac(f);
                    dockGlass.setFrac(f);
                }
            }

            @Override
            public void onPageSelected(int page) {
            }

            @Override
            public void onEmptyLongPress() {
                showSettings();
            }
        });
    }

    private void checkSizes() {
        int rw = root.getWidth(), rh = root.getHeight();
        if (rw > 0 && rh > 0 && (rw != rootW || rh != rootH)) {
            rootW = rw;
            rootH = rh;
            loadBackdrop(rw, rh);
        }
        int pw = pager.getWidth(), ph = pager.getHeight();
        if (pw > 0 && ph > 0 && (pw != pagerW || ph != pagerH)) {
            pagerW = pw;
            pagerH = ph;
            pager.post(new Runnable() {
                @Override
                public void run() {
                    buildPages();
                }
            });
        }
    }

    private void applyOrientation() {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        ViewGroup.LayoutParams lp = dockWrap.getLayoutParams();
        lp.height = dp(land ? 54 : 66);
        dockWrap.setLayoutParams(lp);
    }

    private void applyStyle() {
        backdropView.setStyle(false, 0, false);
        dockGlass.setStyle(blur, blur ? 0x40FFFFFF : 0xB0303030, false);
        spotlight.setStyle(blur, blur ? 0x66000000 : 0xCC000000);
    }

    // ---------------------------------------------------------------- loading

    private void loadBackdrop(final int w, final int h) {
        final Context app = getApplicationContext();
        new AsyncTask<Void, Void, Backdrop>() {
            @Override
            protected Backdrop doInBackground(Void... v) {
                try {
                    return Backdrop.load(app, w, h);
                } catch (Throwable t) {
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Backdrop b) {
                if (b == null || w != rootW || h != rootH) return;
                backdrop = b;
                backdropView.setBackdrop(b, root);
                dockGlass.setBackdrop(b, root);
                spotlight.setBackdrop(b, root);
            }
        }.execute();
    }

    private static class Loaded {
        ArrayList<AppInfo> grid = new ArrayList<AppInfo>();
        ArrayList<AppInfo> dock = new ArrayList<AppInfo>();
    }

    private static final Comparator<AppInfo> BY_LABEL = new Comparator<AppInfo>() {
        @Override
        public int compare(AppInfo a, AppInfo b) {
            return a.lower.compareTo(b.lower);
        }
    };

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final int iconPx = Math.min(128, dp(54));
        final boolean g = gloss;
        final String self = getPackageName();

        new AsyncTask<Void, Void, Loaded>() {
            @Override
            protected Loaded doInBackground(Void... v) {
                Loaded out = new Loaded();
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                ArrayList<AppInfo> all = new ArrayList<AppInfo>();
                for (ResolveInfo ri : ris) {
                    if (self.equals(ri.activityInfo.packageName)) continue;
                    all.add(make(pm, ri, iconPx, g));
                }
                Collections.sort(all, BY_LABEL);

                // Dock, in iOS order: Phone, Messages, Browser, Camera.
                Intent[] wanted = new Intent[4];
                wanted[BuiltinIcons.PHONE] = new Intent(Intent.ACTION_DIAL);
                wanted[BuiltinIcons.MESSAGES] = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"));
                wanted[BuiltinIcons.BROWSER] = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"));
                wanted[BuiltinIcons.CAMERA] = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                HashSet<String> used = new HashSet<String>();
                for (int k = 0; k < wanted.length; k++) {
                    List<ResolveInfo> hits = pm.queryIntentActivities(wanted[k], 0);
                    for (ResolveInfo ri : hits) {
                        String pkg = ri.activityInfo.packageName;
                        if ("android".equals(pkg) || self.equals(pkg) || used.contains(pkg)) continue;
                        AppInfo found = null;
                        for (AppInfo a : all) {
                            if (a.packageName().equals(pkg)) {
                                found = a;
                                break;
                            }
                        }
                        if (found == null) found = make(pm, ri, iconPx, g);
                        found.icon = BuiltinIcons.make(k, iconPx);
                        used.add(pkg);
                        out.dock.add(found);
                        break;
                    }
                }
                for (AppInfo a : all) {
                    if (!used.contains(a.packageName())) out.grid.add(a);
                }
                return out;
            }

            @Override
            protected void onPostExecute(Loaded l) {
                apps.clear();
                apps.addAll(l.grid);
                dockApps.clear();
                dockApps.addAll(l.dock);
                ArrayList<AppInfo> searchable = new ArrayList<AppInfo>(l.grid);
                searchable.addAll(l.dock);
                Collections.sort(searchable, BY_LABEL);
                spotlight.setApps(searchable);
                buildPages();
                buildDock();
            }
        }.execute();
    }

    private AppInfo make(PackageManager pm, ResolveInfo ri, int iconPx, boolean g) {
        ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
        AppInfo a = new AppInfo(String.valueOf(ri.loadLabel(pm)), cn);
        a.removable = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        String key = cn.flattenToString() + (g ? "#g" : "#f") + iconPx;
        Bitmap bmp;
        synchronized (ICONS) {
            bmp = ICONS.get(key);
        }
        if (bmp == null) {
            Drawable dr = ri.loadIcon(pm);
            bmp = IconMaker.make(dr, iconPx, g);
            synchronized (ICONS) {
                ICONS.put(key, bmp);
            }
        }
        a.icon = bmp;
        return a;
    }

    // ---------------------------------------------------------------- pages

    private void buildPages() {
        if (pagerW <= 0 || pagerH <= 0) return;
        int cols = Math.max(4, pagerW / dp(80));
        int rows = Math.max(3, pagerH / dp(70));
        // widgets take two rows when there is room, one row when it is tight
        int headerRows = rows >= 4 ? 2 : 1;
        int keep = pager.getCurrentPage();

        pager.removeAllViews();
        widgets = new WidgetsView(this);
        widgets.setListener(new WidgetsView.Listener() {
            @Override
            public void onWidget(int which) {
                if (which == 0) openByLabel("clock");
                else openMaps();
            }
        });

        int idx = 0;
        int pageIdx = 0;
        while (idx < apps.size() || pageIdx == 0) {
            IconPage page = new IconPage(this);
            page.setGrid(cols, rows);
            int cap = cols * rows;
            if (pageIdx == 0) {
                page.setHeader(widgets, headerRows);
                cap = cols * (rows - headerRows);
            }
            for (int k = 0; k < cap && idx < apps.size(); k++) {
                page.addView(makeIcon(apps.get(idx++), true));
            }
            pager.addView(page, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pageIdx++;
        }
        dots.setCount(pageIdx);
        pager.setPage(Math.min(keep, pageIdx - 1), false);
        dots.setPosition(pager.getCurrentPage());
    }

    private void buildDock() {
        dockRow.removeAllViews();
        for (AppInfo a : dockApps) {
            IconView v = makeIcon(a, false);
            dockRow.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private IconView makeIcon(AppInfo a, boolean label) {
        IconView v = new IconView(this);
        v.bind(a, label);
        v.setEditMode(editMode);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IconView iv = (IconView) view;
                if (editMode) {
                    if (iv.hitBadge()) uninstall(iv.getApp());
                } else {
                    launch(iv.getApp());
                }
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (!editMode) setEditMode(true);
                return true;
            }
        });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                if (has) {
                    int page = pager.pageOf(view);
                    if (page >= 0 && page != pager.getCurrentPage()) pager.snapToPage(page);
                }
            }
        });
        return v;
    }

    private ArrayList<IconView> allIcons() {
        ArrayList<IconView> out = new ArrayList<IconView>();
        for (int p = 0; p < pager.getChildCount(); p++) {
            ViewGroup page = (ViewGroup) pager.getChildAt(p);
            for (int i = 0; i < page.getChildCount(); i++) {
                View v = page.getChildAt(i);
                if (v instanceof IconView) out.add((IconView) v);
            }
        }
        for (int i = 0; i < dockRow.getChildCount(); i++) out.add((IconView) dockRow.getChildAt(i));
        return out;
    }

    private void setEditMode(boolean on) {
        editMode = on;
        for (IconView v : allIcons()) v.setEditMode(on);
    }

    // ---------------------------------------------------------------- actions

    private void launch(AppInfo a) {
        try {
            startActivity(a.launchIntent());
            overridePendingTransition(R.anim.app_open, R.anim.hold);
        } catch (Exception e) {
            Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void openByLabel(String needle) {
        needle = needle.toLowerCase(Locale.getDefault());
        for (AppInfo a : apps) {
            if (a.lower.contains(needle)) {
                launch(a);
                return;
            }
        }
        Toast.makeText(this, "No " + needle + " app found", Toast.LENGTH_SHORT).show();
    }

    private void openMaps() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")));
            overridePendingTransition(R.anim.app_open, R.anim.hold);
        } catch (Exception e) {
            openByLabel("maps");
        }
    }

    private void uninstall(AppInfo a) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.packageName())));
        } catch (Exception e) {
            Toast.makeText(this, "Can't uninstall " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSettings() {
        final boolean[] c = {blur, parallax, gloss};
        new AlertDialog.Builder(this)
                .setTitle("Launcher settings")
                .setMultiChoiceItems(
                        new CharSequence[]{"Blur effects", "Parallax wallpaper", "Glossy icons (iOS 6 style)"},
                        c,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean checked) {
                                c[which] = checked;
                            }
                        })
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean glossChanged = gloss != c[2];
                        blur = c[0];
                        parallax = c[1];
                        gloss = c[2];
                        prefs.edit()
                                .putBoolean("blur", blur)
                                .putBoolean("parallax", parallax)
                                .putBoolean("gloss", gloss)
                                .commit();
                        if (!parallax) {
                            backdropView.setFrac(0f);
                            dockGlass.setFrac(0f);
                        }
                        applyStyle();
                        if (glossChanged) loadApps();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
