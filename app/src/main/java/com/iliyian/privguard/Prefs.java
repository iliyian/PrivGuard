package com.iliyian.privguard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.service.XposedService;

/** Shared configuration. Uses Vector's remote preferences when the service is bound. */
public final class Prefs {
    public static final String GROUP = "privguard_config";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_CLIP_READ = "clip_read";
    public static final String KEY_CLIP_WRITE = "clip_write";
    public static final String KEY_CLIP_TOAST = "clip_toast";
    public static final String KEY_APP_FILTER = "app_filter";
    public static final String KEY_HIDDEN = "hidden_packages";
    public static final String KEY_MIGRATED = "migrated";

    private Prefs() {
    }

    public static SharedPreferences get(Context context) {
        XposedService service = PrivGuardApp.getService();
        if (service != null) {
            return service.getRemotePreferences(GROUP);
        }
        return context.getSharedPreferences("local_config", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return get(context).getBoolean(KEY_ENABLED, true);
    }

    public static boolean clipRead(Context context) {
        return get(context).getBoolean(KEY_CLIP_READ, true);
    }

    public static boolean clipWrite(Context context) {
        return get(context).getBoolean(KEY_CLIP_WRITE, true);
    }

    public static boolean clipToast(Context context) {
        return get(context).getBoolean(KEY_CLIP_TOAST, false);
    }

    public static boolean appFilter(Context context) {
        return get(context).getBoolean(KEY_APP_FILTER, true);
    }

    public static Set<String> hidden(Context context) {
        Set<String> value = get(context).getStringSet(KEY_HIDDEN, null);
        return value == null ? new HashSet<>() : new HashSet<>(value);
    }

    public static void setHidden(Context context, Set<String> hidden) {
        get(context).edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    public static Set<String> emptySet() {
        return Collections.emptySet();
    }
}
