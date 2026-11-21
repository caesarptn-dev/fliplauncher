package com.flip.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;

import java.util.Locale;

/** One launchable app: label, component and a pre-rendered iOS-style icon. */
public class AppInfo {
    public final String label;
    public final String lower;
    public final ComponentName component;
    public Bitmap icon;
    public boolean removable;

    public AppInfo(String label, ComponentName component) {
        this.label = label == null ? "" : label;
        this.lower = this.label.toLowerCase(Locale.getDefault());
        this.component = component;
    }

    public String packageName() {
        return component.getPackageName();
    }

    public Intent launchIntent() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(component);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }
}
