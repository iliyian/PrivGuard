package com.iliyian.privguard;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.XposedService;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_INSTALLED_APPS = 1001;
    private static final String PERMISSION_GET_INSTALLED_APPS =
            "com.android.permission.GET_INSTALLED_APPS";

    private TextView statusTitle;
    private TextView statusDetail;
    private ImageView statusIcon;
    private View statusDot;
    private TextView hiddenSummary;
    private TextView scopeSummary;
    private MaterialSwitch switchEnabled;
    private MaterialSwitch switchClipRead;
    private MaterialSwitch switchClipWrite;
    private MaterialSwitch switchClipToast;
    private MaterialSwitch switchAppFilter;
    private Button btnPickHidden;
    private Button btnPickScope;
    private Button btnRefresh;
    private View rowEnabled;
    private View rowClipRead;
    private View rowClipWrite;
    private View rowClipToast;
    private View rowAppFilter;

    private final Runnable serviceListener = this::refresh;
    private boolean updating;
    private Runnable pendingPick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        statusTitle = findViewById(R.id.statusTitle);
        statusDetail = findViewById(R.id.statusDetail);
        statusIcon = findViewById(R.id.statusIcon);
        statusDot = findViewById(R.id.statusDot);
        hiddenSummary = findViewById(R.id.hiddenSummary);
        scopeSummary = findViewById(R.id.scopeSummary);
        switchEnabled = findViewById(R.id.switchEnabled);
        switchClipRead = findViewById(R.id.switchClipRead);
        switchClipWrite = findViewById(R.id.switchClipWrite);
        switchClipToast = findViewById(R.id.switchClipToast);
        switchAppFilter = findViewById(R.id.switchAppFilter);
        btnPickHidden = findViewById(R.id.btnPickHidden);
        btnPickScope = findViewById(R.id.btnPickScope);
        btnRefresh = findViewById(R.id.btnRefresh);
        rowEnabled = findViewById(R.id.rowEnabled);
        rowClipRead = findViewById(R.id.rowClipRead);
        rowClipWrite = findViewById(R.id.rowClipWrite);
        rowClipToast = findViewById(R.id.rowClipToast);
        rowAppFilter = findViewById(R.id.rowAppFilter);

        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updating) {
                saveBoolean(Prefs.KEY_ENABLED, isChecked);
                refresh();
            }
        });
        switchClipRead.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updating) {
                saveBoolean(Prefs.KEY_CLIP_READ, isChecked);
            }
        });
        switchClipWrite.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updating) {
                saveBoolean(Prefs.KEY_CLIP_WRITE, isChecked);
            }
        });
        switchClipToast.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updating) {
                saveBoolean(Prefs.KEY_CLIP_TOAST, isChecked);
            }
        });
        switchAppFilter.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updating) {
                saveBoolean(Prefs.KEY_APP_FILTER, isChecked);
            }
        });

        rowEnabled.setOnClickListener(v -> switchEnabled.toggle());
        rowClipRead.setOnClickListener(v -> switchClipRead.toggle());
        rowClipWrite.setOnClickListener(v -> switchClipWrite.toggle());
        rowClipToast.setOnClickListener(v -> switchClipToast.toggle());
        rowAppFilter.setOnClickListener(v -> switchAppFilter.toggle());

        btnPickHidden.setOnClickListener(v -> pickHiddenApps());
        btnPickScope.setOnClickListener(v -> pickScopeApps());
        btnRefresh.setOnClickListener(v -> refresh());
    }

    @Override
    protected void onStart() {
        super.onStart();
        PrivGuardApp.addListener(serviceListener);
        refresh();
    }

    @Override
    protected void onStop() {
        PrivGuardApp.removeListener(serviceListener);
        super.onStop();
    }

    private void refresh() {
        runOnUiThread(() -> {
            SharedPreferences prefs = Prefs.get(this);
            updating = true;
            boolean enabled = prefs.getBoolean(Prefs.KEY_ENABLED, true);
            switchEnabled.setChecked(enabled);
            switchClipRead.setChecked(prefs.getBoolean(Prefs.KEY_CLIP_READ, true));
            switchClipWrite.setChecked(prefs.getBoolean(Prefs.KEY_CLIP_WRITE, true));
            switchClipToast.setChecked(prefs.getBoolean(Prefs.KEY_CLIP_TOAST, false));
            switchAppFilter.setChecked(prefs.getBoolean(Prefs.KEY_APP_FILTER, true));
            updating = false;

            XposedService service = PrivGuardApp.getService();
            int scopeCount = 0;
            if (service == null) {
                statusTitle.setText("未连接 Vector 服务");
                statusDetail.setText("在 Vector 管理器中启用 PrivGuard 后生效");
                statusDot.getBackground().mutate().setTint(
                        ContextCompat.getColor(this, R.color.status_error));
                statusIcon.setAlpha(0.6f);
                scopeSummary.setText("未连接");
                btnPickScope.setEnabled(false);
                btnPickScope.setAlpha(0.5f);
            } else {
                try {
                    statusTitle.setText(enabled ? "防护已开启" : "防护已暂停");
                    List<String> scope = service.getScope();
                    scopeCount = scope.size();
                    statusDetail.setText(String.format(Locale.US, "%s · API %d · 作用域 %d 个应用",
                            service.getFrameworkName(),
                            service.getApiVersion(),
                            scopeCount));
                    statusDot.getBackground().mutate().setTint(
                            ContextCompat.getColor(this, enabled ? R.color.status_ok : R.color.status_warn));
                    statusIcon.setAlpha(1f);
                    scopeSummary.setText(scopeCount == 0
                            ? "没有生效应用，模块不会注入任何应用"
                            : "生效 " + scopeCount + " 个应用：" + describeLabels(scope));
                    btnPickScope.setEnabled(true);
                    btnPickScope.setAlpha(1f);
                } catch (Throwable t) {
                    statusTitle.setText("Vector 服务不可用");
                    statusDetail.setText(String.valueOf(t.getMessage()));
                    statusDot.getBackground().mutate().setTint(
                            ContextCompat.getColor(this, R.color.status_error));
                    scopeSummary.setText("读取作用域失败");
                    btnPickScope.setEnabled(false);
                    btnPickScope.setAlpha(0.5f);
                }
            }

            Set<String> hidden = Prefs.hidden(this);
            if (hidden.isEmpty()) {
                hiddenSummary.setText("未选择（不会过滤任何应用）");
            } else {
                hiddenSummary.setText("已隐藏 " + hidden.size() + " 个应用：" + describeLabels(hidden));
            }
        });
    }

    private void saveBoolean(String key, boolean value) {
        Prefs.get(this).edit().putBoolean(key, value).apply();
        if (PrivGuardApp.getService() == null) {
            Toast.makeText(this, "已保存到本地；连接 Vector 后会自动同步", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickHiddenApps() {
        Set<String> selected = Prefs.hidden(this);
        withAppListPermission(() -> AppPickerDialog.show(this, "选择要隐藏的应用", loadApps(), selected, result -> {
            Prefs.setHidden(this, result);
            refresh();
            Toast.makeText(this, "隐藏列表已保存", Toast.LENGTH_SHORT).show();
        }));
    }

    private void withAppListPermission(Runnable action) {
        try {
            if (checkSelfPermission(PERMISSION_GET_INSTALLED_APPS) == PackageManager.PERMISSION_GRANTED) {
                action.run();
                return;
            }
        } catch (Throwable ignored) {
        }
        pendingPick = action;
        try {
            requestPermissions(new String[]{PERMISSION_GET_INSTALLED_APPS}, REQUEST_INSTALLED_APPS);
        } catch (Throwable t) {
            pendingPick = null;
            action.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_INSTALLED_APPS) {
            return;
        }
        Runnable action = pendingPick;
        pendingPick = null;
        if (action == null) {
            return;
        }
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予应用列表权限，列表可能不完整", Toast.LENGTH_SHORT).show();
        }
        action.run();
    }

    private void pickScopeApps() {
        XposedService service = PrivGuardApp.getService();
        if (service == null) {
            Toast.makeText(this, "未连接 Vector 服务", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> rawScope;
        try {
            rawScope = new ArrayList<>(service.getScope());
        } catch (Throwable t) {
            Toast.makeText(this, "读取作用域失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> rawByBase = new HashMap<>();
        Set<String> selected = new HashSet<>();
        for (String value : rawScope) {
            String base = basePackage(value);
            rawByBase.put(base, value);
            selected.add(base);
        }
        selected.remove(getPackageName());

        withAppListPermission(() -> AppPickerDialog.show(this, "选择生效应用", loadApps(), selected, result -> {
            result.remove(getPackageName());
            List<String> added = new ArrayList<>();
            for (String pkg : result) {
                if (!selected.contains(pkg)) {
                    added.add(pkg);
                }
            }
            List<String> removed = new ArrayList<>();
            for (String pkg : selected) {
                if (!result.contains(pkg)) {
                    String raw = rawByBase.get(pkg);
                    removed.add(raw != null ? raw : pkg);
                }
            }
            applyScope(service, added, removed);
        }));
    }

    private void applyScope(XposedService service, List<String> added, List<String> removed) {
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        if (!removed.isEmpty()) {
            try {
                service.removeScope(removed);
            } catch (Throwable t) {
                Toast.makeText(this, "移除作用域失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        if (added.isEmpty()) {
            refresh();
            Toast.makeText(this, "作用域已更新，建议强制停止目标应用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            service.requestScope(added, new XposedService.OnScopeEventListener() {
                @Override
                public void onScopeRequestApproved(List<String> approved) {
                    runOnUiThread(() -> {
                        refresh();
                        Toast.makeText(MainActivity.this,
                                "作用域已更新，建议强制停止目标应用", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onScopeRequestFailed(String message) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "添加作用域失败：" + message, Toast.LENGTH_LONG).show());
                }
            });
        } catch (Throwable t) {
            Toast.makeText(this, "添加作用域失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private List<AppEntry> loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppEntry> apps = new ArrayList<>(installed.size());
        for (ApplicationInfo info : installed) {
            if (getPackageName().equals(info.packageName)) {
                continue;
            }
            CharSequence label = pm.getApplicationLabel(info);
            String name = label == null ? info.packageName : label.toString();
            boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            apps.add(new AppEntry(info.packageName, name, system));
        }
        apps.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return apps;
    }

    private String describeLabels(Iterable<String> packages) {
        List<String> labels = new ArrayList<>();
        int count = 0;
        for (String pkg : packages) {
            count++;
            if (labels.size() < 4) {
                labels.add(labelFor(basePackage(pkg)));
            }
        }
        String summary = String.join("、", labels);
        if (count > labels.size()) {
            summary += "…";
        }
        return summary;
    }

    private String labelFor(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? pkg : label.toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private static String basePackage(String value) {
        int slash = value.indexOf('/');
        return slash > 0 ? value.substring(0, slash) : value;
    }
}
