package com.iliyian.privguard;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.VersionedPackage;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.ExceptionMode;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/**
 * Runs inside each app selected in Vector's scope. It never touches system_server.
 */
public class PrivGuardModule extends XposedModule {
    private static final String TAG = "PrivGuard";

    private static final Set<String> THROWING_METHODS = new HashSet<>(Arrays.asList(
            "getPackageInfo",
            "getApplicationInfo",
            "getActivityInfo",
            "getServiceInfo",
            "getProviderInfo",
            "getReceiverInfo"
    ));

    private static final Set<String> PACKAGE_METHODS = new HashSet<>(Arrays.asList(
            "getInstalledPackages",
            "getInstalledApplications",
            "getPackagesHoldingPermissions",
            "getPackageInfo",
            "getApplicationInfo",
            "getActivityInfo",
            "getServiceInfo",
            "getProviderInfo",
            "getReceiverInfo",
            "queryIntentActivities",
            "queryIntentServices",
            "queryIntentProviders",
            "queryIntentReceivers",
            "queryIntentContentProviders",
            "resolveActivity",
            "resolveService",
            "getLaunchIntentForPackage",
            "getLeanbackLaunchIntentForPackage"
    ));

    private volatile SharedPreferences prefs;
    private volatile long lastToastAt;
    private volatile String currentPackage;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Log.i(TAG, "module loaded in " + param.getProcessName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        String packageName = param.getPackageName();
        if (packageName == null) {
            return;
        }
        // Do not touch the system process or SystemUI.
        if ("android".equals(packageName) || "com.android.systemui".equals(packageName)) {
            return;
        }
        ApplicationInfo self = getModuleApplicationInfo();
        if (self != null && packageName.equals(self.packageName)) {
            return;
        }
        currentPackage = packageName;

        try {
            prefs = getRemotePreferences(Prefs.GROUP);
        } catch (Throwable t) {
            Log.e(TAG, "getRemotePreferences failed in " + packageName, t);
            return;
        }

        try {
            hookClipboard(param.getClassLoader());
        } catch (Throwable t) {
            Log.e(TAG, "clipboard hook failed in " + packageName, t);
        }
        try {
            hookPackageManager(param.getClassLoader());
        } catch (Throwable t) {
            Log.e(TAG, "package hook failed in " + packageName, t);
        }
        Log.i(TAG, "hooks installed in " + packageName);
    }

    private void hookClipboard(ClassLoader classLoader) throws Throwable {
        Class<?> clipboardManager = Class.forName("android.content.ClipboardManager", false, classLoader);
        for (final Method method : clipboardManager.getDeclaredMethods()) {
            final String name = method.getName();
            if (!"getPrimaryClip".equals(name)
                    && !"getPrimaryClipDescription".equals(name)
                    && !"hasPrimaryClip".equals(name)
                    && !"setPrimaryClip".equals(name)) {
                continue;
            }
            hook(method).intercept(chain -> {
                switch (name) {
                    case "getPrimaryClip":
                    case "getPrimaryClipDescription":
                        if (blocked(Prefs.KEY_CLIP_READ, true)) {
                            toast("PrivGuard：已拦截剪贴板读取");
                            return null;
                        }
                        break;
                    case "hasPrimaryClip":
                        if (blocked(Prefs.KEY_CLIP_READ, true)) {
                            return false;
                        }
                        break;
                    case "setPrimaryClip":
                        if (blocked(Prefs.KEY_CLIP_WRITE, true)) {
                            toast("PrivGuard：已拦截剪贴板写入");
                            return null;
                        }
                        break;
                    default:
                        break;
                }
                return chain.proceed();
            });
        }
    }

    private void hookPackageManager(ClassLoader classLoader) throws Throwable {
        Class<?> packageManager = Class.forName("android.app.ApplicationPackageManager", false, classLoader);
        for (final Method method : packageManager.getDeclaredMethods()) {
            final String name = method.getName();
            if (!PACKAGE_METHODS.contains(name)) {
                continue;
            }
            if (THROWING_METHODS.contains(name)) {
                hook(method)
                        .setExceptionMode(ExceptionMode.PASSTHROUGH)
                        .intercept(chain -> handlePackageCall(name, chain));
            } else {
                hook(method).intercept(chain -> handlePackageCall(name, chain));
            }
        }
    }

    private Object handlePackageCall(String name, Chain chain) throws Throwable {
        if (!filterEnabled()) {
            return chain.proceed();
        }
        Set<String> hidden = hiddenPackages();
        if (hidden.isEmpty()) {
            return chain.proceed();
        }
        if (THROWING_METHODS.contains(name)) {
            String pkg = packageArgument(chain);
            if (pkg != null && hidden.contains(pkg)) {
                throw new PackageManager.NameNotFoundException(pkg);
            }
            return chain.proceed();
        }
        Object result = chain.proceed();
        switch (name) {
            case "getInstalledPackages":
            case "getInstalledApplications":
            case "getPackagesHoldingPermissions":
            case "queryIntentActivities":
            case "queryIntentServices":
            case "queryIntentProviders":
            case "queryIntentReceivers":
            case "queryIntentContentProviders":
                return filterList(result, hidden);
            case "resolveActivity":
            case "resolveService":
                return isHidden(result, hidden) ? null : result;
            case "getLaunchIntentForPackage":
            case "getLeanbackLaunchIntentForPackage": {
                String pkg = packageArgument(chain);
                return pkg != null && hidden.contains(pkg) ? null : result;
            }
            default:
                return result;
        }
    }

    private Object filterList(Object result, Set<String> hidden) {
        if (!(result instanceof List)) {
            return result;
        }
        List<?> source = (List<?>) result;
        ArrayList<Object> filtered = new ArrayList<>(source.size());
        for (Object item : source) {
            if (!isHidden(item, hidden)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean isHidden(Object item, Set<String> hidden) {
        String pkg = packageOf(item);
        return pkg != null && hidden.contains(pkg);
    }

    private String packageOf(Object item) {
        if (item instanceof PackageInfo) {
            return ((PackageInfo) item).packageName;
        }
        if (item instanceof ApplicationInfo) {
            return ((ApplicationInfo) item).packageName;
        }
        if (item instanceof ResolveInfo) {
            ResolveInfo info = (ResolveInfo) item;
            if (info.activityInfo != null) {
                return info.activityInfo.packageName;
            }
            if (info.serviceInfo != null) {
                return info.serviceInfo.packageName;
            }
            if (info.providerInfo != null) {
                return info.providerInfo.packageName;
            }
            return info.resolvePackageName;
        }
        if (item instanceof ComponentInfo) {
            return ((ComponentInfo) item).packageName;
        }
        return null;
    }

    private String packageArgument(Chain chain) {
        List<Object> args = chain.getArgs();
        if (args.isEmpty() || args.get(0) == null) {
            return null;
        }
        Object first = args.get(0);
        if (first instanceof String) {
            return (String) first;
        }
        if (first instanceof ComponentName) {
            return ((ComponentName) first).getPackageName();
        }
        if (first instanceof VersionedPackage) {
            return ((VersionedPackage) first).getPackageName();
        }
        return null;
    }

    private boolean blocked(String key, boolean defaultValue) {
        SharedPreferences snapshot = prefs;
        if (snapshot == null) {
            return false;
        }
        try {
            return snapshot.getBoolean(Prefs.KEY_ENABLED, true)
                    && snapshot.getBoolean(key, defaultValue);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean filterEnabled() {
        SharedPreferences snapshot = prefs;
        if (snapshot == null) {
            return false;
        }
        try {
            return snapshot.getBoolean(Prefs.KEY_ENABLED, true)
                    && snapshot.getBoolean(Prefs.KEY_APP_FILTER, true);
        } catch (Throwable t) {
            return false;
        }
    }

    private Set<String> hiddenPackages() {
        SharedPreferences snapshot = prefs;
        if (snapshot == null) {
            return Collections.emptySet();
        }
        try {
            Set<String> hidden = snapshot.getStringSet(Prefs.KEY_HIDDEN, null);
            if (hidden == null) {
                return Collections.emptySet();
            }
            Set<String> result = new HashSet<>(hidden);
            // An app must always be able to see itself, even if the user selected every package.
            if (currentPackage != null) {
                result.remove(currentPackage);
            }
            return result;
        } catch (Throwable t) {
            return Collections.emptySet();
        }
    }

    private void toast(String message) {
        SharedPreferences snapshot = prefs;
        if (snapshot == null) {
            return;
        }
        try {
            if (!snapshot.getBoolean(Prefs.KEY_CLIP_TOAST, false)) {
                return;
            }
        } catch (Throwable t) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastToastAt < 1500) {
            return;
        }
        lastToastAt = now;
        Context context = currentApplication();
        if (context == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method method = activityThread.getDeclaredMethod("currentApplication");
            method.setAccessible(true);
            Object value = method.invoke(null);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
