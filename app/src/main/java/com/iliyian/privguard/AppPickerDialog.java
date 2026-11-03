package com.iliyian.privguard;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searchable multi-select app picker. */
public final class AppPickerDialog {
    public interface OnAppsPicked {
        void onPicked(Set<String> packages);
    }

    private AppPickerDialog() {
    }

    public static void show(Context context, String title, List<AppEntry> apps,
                            Set<String> initial, OnAppsPicked callback) {
        List<AppEntry> all = new ArrayList<>(apps);
        Set<String> selected = new HashSet<>(initial);
        List<AppEntry> filtered = new ArrayList<>(all);

        if (all.isEmpty()) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(title)
                    .setMessage("没有读取到应用列表。请在系统权限中允许 PrivGuard 获取应用列表后重试。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 8);
        container.setPadding(padding, padding, padding, 0);

        TextInputLayout searchBox = new TextInputLayout(context);
        searchBox.setHint("搜索应用名或包名");
        searchBox.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText searchInput = new TextInputEditText(context);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                searchInput.clearFocus();
                InputMethodManager imm =
                        (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        searchBox.addView(searchInput);
        container.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout quickRow = new LinearLayout(context);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        com.google.android.material.button.MaterialButton selectAll =
                new com.google.android.material.button.MaterialButton(context, null,
                        android.R.attr.borderlessButtonStyle);
        selectAll.setText("全选");
        com.google.android.material.button.MaterialButton clearAll =
                new com.google.android.material.button.MaterialButton(context, null,
                        android.R.attr.borderlessButtonStyle);
        clearAll.setText("清空");
        quickRow.addView(selectAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        quickRow.addView(clearAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        container.addView(quickRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setFastScrollEnabled(true);
        AppAdapter adapter = new AppAdapter(context, filtered, selected);
        list.setAdapter(adapter);
        int height = Math.min(dp(context, 420),
                (int) (context.getResources().getDisplayMetrics().heightPixels * 0.52f));
        container.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        list.setOnItemClickListener((parent, view, position, id) -> {
            AppEntry entry = adapter.getItem(position);
            if (entry == null) {
                return;
            }
            if (!selected.remove(entry.packageName)) {
                selected.add(entry.packageName);
            }
            adapter.notifyDataSetChanged();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                filtered.clear();
                if (query.isEmpty()) {
                    filtered.addAll(all);
                } else {
                    for (AppEntry entry : all) {
                        if (entry.label.toLowerCase(Locale.ROOT).contains(query)
                                || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                            filtered.add(entry);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        selectAll.setOnClickListener(v -> {
            for (AppEntry entry : filtered) {
                selected.add(entry.packageName);
            }
            adapter.notifyDataSetChanged();
        });
        clearAll.setOnClickListener(v -> {
            for (AppEntry entry : filtered) {
                selected.remove(entry.packageName);
            }
            adapter.notifyDataSetChanged();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("保存", (dialogInterface, which) ->
                        callback.onPicked(new HashSet<>(selected)))
                .setNegativeButton("取消", null)
                .show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class AppAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final List<AppEntry> items;
        private final Set<String> selected;

        AppAdapter(Context context, List<AppEntry> items, Set<String> selected) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.selected = selected;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public AppEntry getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = inflater.inflate(R.layout.item_app_picker, parent, false);
            }
            AppEntry entry = getItem(position);
            TextView title = view.findViewById(R.id.title);
            TextView subtitle = view.findViewById(R.id.subtitle);
            MaterialCheckBox check = view.findViewById(R.id.check);
            title.setText(entry.label + (entry.system ? "  [系统]" : ""));
            subtitle.setText(entry.packageName);
            check.setChecked(selected.contains(entry.packageName));
            return view;
        }
    }
}
