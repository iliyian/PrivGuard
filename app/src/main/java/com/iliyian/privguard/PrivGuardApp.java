package com.iliyian.privguard;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public class PrivGuardApp extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(this);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onServiceBind(XposedService service) {
        PrivGuardApp.service = service;
        try {
            migrateLocalToRemote(service);
        } catch (Throwable ignored) {
        }
        notifyListeners();
    }

    @Override
    public void onServiceDied(XposedService service) {
        PrivGuardApp.service = null;
        notifyListeners();
    }

    public static XposedService getService() {
        return service;
    }

    public static void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        MAIN.post(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    private void migrateLocalToRemote(XposedService service) {
        SharedPreferences local = getSharedPreferences("local_config", MODE_PRIVATE);
        SharedPreferences remote = service.getRemotePreferences(Prefs.GROUP);
        if (remote.getBoolean(Prefs.KEY_MIGRATED, false)) {
            return;
        }
        SharedPreferences.Editor editor = remote.edit();
        editor.putBoolean(Prefs.KEY_ENABLED, local.getBoolean(Prefs.KEY_ENABLED, true));
        editor.putBoolean(Prefs.KEY_CLIP_READ, local.getBoolean(Prefs.KEY_CLIP_READ, true));
        editor.putBoolean(Prefs.KEY_CLIP_WRITE, local.getBoolean(Prefs.KEY_CLIP_WRITE, true));
        editor.putBoolean(Prefs.KEY_CLIP_TOAST, local.getBoolean(Prefs.KEY_CLIP_TOAST, false));
        editor.putBoolean(Prefs.KEY_APP_FILTER, local.getBoolean(Prefs.KEY_APP_FILTER, true));
        Set<String> hidden = local.getStringSet(Prefs.KEY_HIDDEN, null);
        if (hidden != null) {
            editor.putStringSet(Prefs.KEY_HIDDEN, new HashSet<>(hidden));
        }
        editor.putBoolean(Prefs.KEY_MIGRATED, true);
        editor.apply();
    }
}
