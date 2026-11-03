package com.iliyian.privguard.test;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = "PrivGuardTest";
    private static final int REQUEST_INSTALLED_APPS = 7;
    private static final String PERMISSION_GET_INSTALLED_APPS =
            "com.android.permission.GET_INSTALLED_APPS";

    private TextView clipboardText;
    private TextView listText;
    private EditText editWrite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clipboardText = findViewById(R.id.clipboardText);
        listText = findViewById(R.id.listText);
        editWrite = findViewById(R.id.editWrite);
        Button btnRead = findViewById(R.id.btnRead);
        Button btnWrite = findViewById(R.id.btnWrite);
        Button btnList = findViewById(R.id.btnList);
        Button btnCheck = findViewById(R.id.btnCheck);
        btnRead.setOnClickListener(v -> readClipboard());
        btnWrite.setOnClickListener(v -> writeClipboard());
        btnList.setOnClickListener(v -> listApps());
        btnCheck.setOnClickListener(v -> checkWeChat());
        readClipboard();
        ensureListPermission();
    }

    private void ensureListPermission() {
        try {
            if (checkSelfPermission(PERMISSION_GET_INSTALLED_APPS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                listApps();
                return;
            }
        } catch (Throwable ignored) {
        }
        requestPermissions(new String[]{PERMISSION_GET_INSTALLED_APPS}, REQUEST_INSTALLED_APPS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_INSTALLED_APPS) {
            listApps();
        }
    }

    private void readClipboard() {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = manager.getPrimaryClip();
            String text = "null";
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence value = clip.getItemAt(0).coerceToText(this);
                text = value == null ? "null" : value.toString();
            }
            String message = "剪贴板: " + text;
            clipboardText.setText(message);
            Log.i(TAG, "READ " + message);
        } catch (Throwable t) {
            clipboardText.setText("读取失败: " + t);
            Log.i(TAG, "READ failed", t);
        }
    }

    private void writeClipboard() {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("test", editWrite.getText()));
            Log.i(TAG, "WRITE ok: " + editWrite.getText());
        } catch (Throwable t) {
            Log.i(TAG, "WRITE failed", t);
        }
    }

    private void checkWeChat() {
        try {
            Log.i(TAG, "PM class=" + getPackageManager().getClass().getName());
            for (java.lang.reflect.Method method : getPackageManager().getClass().getMethods()) {
                if ("getPackageInfo".equals(method.getName())) {
                    Log.i(TAG, "PM method=" + method
                            + " declared=" + method.getDeclaringClass().getName());
                }
            }
            boolean installed = false;
            for (PackageInfo info : getPackageManager().getInstalledPackages(0)) {
                if ("com.tencent.mm".equals(info.packageName)) {
                    installed = true;
                    break;
                }
            }
            boolean packageInfo;
            try {
                getPackageManager().getPackageInfo("com.tencent.mm", 0);
                packageInfo = true;
            } catch (Throwable t) {
                packageInfo = false;
            }
            boolean query = false;
            android.content.Intent launch = new android.content.Intent(
                    android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            for (android.content.pm.ResolveInfo info :
                    getPackageManager().queryIntentActivities(launch, 0)) {
                if (info.activityInfo != null
                        && "com.tencent.mm".equals(info.activityInfo.packageName)) {
                    query = true;
                    break;
                }
            }
            String result = "installedList=" + installed
                    + "\ngetPackageInfo=" + packageInfo
                    + "\nqueryIntentActivities=" + query;
            listText.setText(result);
            Log.i(TAG, "CHECK " + result.replace('\n', ' '));
        } catch (Throwable t) {
            Log.i(TAG, "CHECK failed", t);
        }
    }

    private void listApps() {
        try {
            List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
            boolean containsWeChat = false;
            for (PackageInfo info : packages) {
                if ("com.tencent.mm".equals(info.packageName)) {
                    containsWeChat = true;
                    break;
                }
            }
            Collections.sort(packages, (a, b) -> a.packageName.compareTo(b.packageName));
            StringBuilder builder = new StringBuilder();
            builder.append("应用数: ").append(packages.size());
            builder.append("\n含 com.tencent.mm: ").append(containsWeChat);
            builder.append("\n前 15 个:");
            for (int i = 0; i < Math.min(15, packages.size()); i++) {
                builder.append('\n').append(packages.get(i).packageName);
            }
            listText.setText(builder.toString());
            Log.i(TAG, "LIST count=" + packages.size() + " wechat=" + containsWeChat);
        } catch (Throwable t) {
            listText.setText("读取应用列表失败: " + t);
            Log.i(TAG, "LIST failed", t);
        }
    }
}
