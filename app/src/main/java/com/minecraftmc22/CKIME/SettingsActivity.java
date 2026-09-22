package com.minecraftmc22.CKIME;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 设置界面：新建 / 编辑 / 删除自定义按键。
 * 保存后立即生效，键盘顶部的自定义按键行会实时刷新。
 */
public class SettingsActivity extends Activity {

    private List<CustomKey> keys;
    private BaseAdapter adapter;
    private TextView emptyView;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        keys = KeyStore.load(this);

        ListView list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty);

        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return keys.size();
            }

            @Override
            public Object getItem(int position) {
                return keys.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null ? convertView
                        : LayoutInflater.from(SettingsActivity.this)
                                .inflate(R.layout.item_key, parent, false);
                CustomKey k = keys.get(position);
                ((TextView) v.findViewById(R.id.tvText)).setText(k.text);
                ((TextView) v.findViewById(R.id.tvSub)).setText(
                        k.autoEnter ? "点击输入并自动回车（发送）" : "仅输入，不回车");
                return v;
            }
        };
        list.setAdapter(adapter);

        // 点击编辑，长按删除
        list.setOnItemClickListener((parent, view, position, id) -> showKeyDialog(position));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            keys.remove(position);
            KeyStore.save(SettingsActivity.this, keys);
            refresh();
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            return true;
        });

        findViewById(R.id.btnAdd).setOnClickListener(v -> showKeyDialog(-1));
        tvStatus = findViewById(R.id.tvStatus);
        findViewById(R.id.btnImeSettings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        findViewById(R.id.btnPicker).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });

        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 从系统设置返回后刷新启用状态
        updateImeStatus();
    }

    /** 检测本输入法的启用/选中状态，并给出下一步引导 */
    private void updateImeStatus() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        String imeId = new ComponentName(this, ImeService.class).flattenToString();

        boolean enabled = false;
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (info.getId().equals(imeId)) {
                enabled = true;
                break;
            }
        }
        boolean current = imeId.equals(Settings.Secure.getString(
                getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));

        if (current) {
            tvStatus.setText("✅ 已启用并设为当前输入法\n去任意聊天框试试：点键盘顶部的自定义按键即可自动输入并发送");
        } else if (enabled) {
            tvStatus.setText("⚠️ 已启用，但还不是当前输入法\n点击下方「切换为当前输入法」，在弹出的列表里选择「自定义按键输入法」");
        } else {
            tvStatus.setText("❌ 尚未启用\n1. 点击「去系统设置启用本输入法」\n2. 找到 语言和输入法 → 虚拟键盘 / 管理键盘 / 可用输入法\n3. 打开「自定义按键输入法」的开关\n4. 返回本页，点「切换为当前输入法」");
        }
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(keys.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * index = -1 表示新建，否则编辑对应项。
     */
    private void showKeyDialog(final int index) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, 0);

        final EditText et = new EditText(this);
        et.setHint("按键输入的内容，例如：12");
        if (index >= 0) et.setText(keys.get(index).text);
        box.addView(et);

        final CheckBox cb = new CheckBox(this);
        cb.setText("输入后自动按回车（自动发送）");
        cb.setChecked(index >= 0 ? keys.get(index).autoEnter : true);
        box.addView(cb);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑按键" : "新建按键")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String t = et.getText().toString();
                    if (t.isEmpty()) {
                        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CustomKey k = new CustomKey(t, cb.isChecked());
                    if (index >= 0) keys.set(index, k);
                    else keys.add(k);
                    KeyStore.save(SettingsActivity.this, keys);
                    refresh();
                    Toast.makeText(this, "已保存，键盘顶部即可使用", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null);

        if (index >= 0) {
            builder.setNeutralButton("删除", (d, w) -> {
                keys.remove(index);
                KeyStore.save(SettingsActivity.this, keys);
                refresh();
            });
        }
        builder.show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
