package com.minecraftmc22.ckime;

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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义按键管理（独立界面）。
 *
 * 特性：
 *  - 多个分组：每个分组有名称、独立的顺序；可新建 / 重命名 / 删除
 *  - 按键上下移动：每个按键行内嵌 ▲ / ▼ 按钮，立即生效
 *  - 按键跨组移动：按键 ⋯ 菜单 → 移动到分组（含「+ 新分组」）
 *  - 输入法启用状态：顶部状态栏实时显示 + 一键启用 / 切换入口
 *  - 与键盘 IME 实时联动：保存后键盘顶部按键行立即刷新（通过 onSharedPreferenceChanged）
 */
public class KeysActivity extends Activity {

    private List<KeyGroup> groups;
    private final List<Row> rows = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView tvEmpty;
    private TextView tvImeStatus;

    private static final int TYPE_GROUP = 0;
    private static final int TYPE_KEY = 1;

    /** 列表行：分组头 或 按键项。记录原始下标，方便 O(1) 修改。 */
    private static class Row {
        final int type;
        final int groupIdx;          // 分组在 groups 里的下标
        final int keyIdx;            // 按键在 group.keys 里的下标（仅 TYPE_KEY）
        final KeyGroup group;
        final CustomKey key;

        Row(int type, int groupIdx, int keyIdx, KeyGroup group, CustomKey key) {
            this.type = type;
            this.groupIdx = groupIdx;
            this.keyIdx = keyIdx;
            this.group = group;
            this.key = key;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keys);

        groups = KeyStore.loadGroups(this);
        rebuildRows();

        tvImeStatus = findViewById(R.id.tvImeStatus);
        tvEmpty = findViewById(R.id.empty);

        adapter = new BaseAdapter() {
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) { return rows.get(position).type; }
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                Row row = rows.get(position);
                if (row.type == TYPE_GROUP) return bindGroup(row, convertView, parent);
                return bindKey(row, convertView, parent);
            }
        };

        ListView list = findViewById(R.id.list);
        list.setAdapter(adapter);

        // 点击按键区域 → 编辑对话框（按钮区域会自己拦截点击）
        list.setOnItemClickListener((parent, view, position, id) -> {
            Row r = rows.get(position);
            if (r.type == TYPE_KEY) showKeyDialog(r.groupIdx, r.keyIdx);
        });

        findViewById(R.id.btnAddKey).setOnClickListener(v -> showKeyDialog(-1, -1));
        findViewById(R.id.btnAddGroup).setOnClickListener(v -> showNewGroupDialog());
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnImeSettings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        findViewById(R.id.btnPicker).setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });

        refresh();
        updateImeStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateImeStatus();
    }

    // ------------------------------------------------------------------
    // 列表行绑定
    // ------------------------------------------------------------------

    private View bindGroup(Row row, View cv, ViewGroup parent) {
        View v = cv != null ? cv : LayoutInflater.from(this)
                .inflate(R.layout.item_group, parent, false);
        TextView tvName = v.findViewById(R.id.tvGroupName);
        TextView tvCount = v.findViewById(R.id.tvGroupCount);
        Button btnAdd = v.findViewById(R.id.btnAddKeyHere);
        Button btnMenu = v.findViewById(R.id.btnGroupMenu);

        tvName.setText(row.group.name);
        tvCount.setText("(" + row.group.keys.size() + ")");

        final int gi = row.groupIdx;
        btnAdd.setOnClickListener(vv -> showKeyDialog(gi, -1));
        btnMenu.setOnClickListener(vv -> showGroupMenu(vv, gi));
        v.setOnLongClickListener(vv -> {
            showGroupMenu(btnMenu, gi);
            return true;
        });
        return v;
    }

    private View bindKey(Row row, View cv, ViewGroup parent) {
        View v = cv != null && cv.findViewById(R.id.btnUp) != null
                ? cv
                : LayoutInflater.from(this).inflate(R.layout.item_key, parent, false);
        TextView tvText = v.findViewById(R.id.tvText);
        TextView tvSub = v.findViewById(R.id.tvSub);
        Button btnUp = v.findViewById(R.id.btnUp);
        Button btnDown = v.findViewById(R.id.btnDown);
        Button btnMenu = v.findViewById(R.id.btnMenu);

        tvText.setText(row.key.text);
        tvSub.setText(row.key.autoEnter ? "自动回车（发送）" : "仅输入");

        final int gi = row.groupIdx;
        final int ki = row.keyIdx;
        final boolean isFirst = ki == 0;
        final boolean isLast = ki == row.group.keys.size() - 1;
        btnUp.setEnabled(!isFirst);
        btnDown.setEnabled(!isLast);
        btnUp.setAlpha(isFirst ? 0.3f : 1f);
        btnDown.setAlpha(isLast ? 0.3f : 1f);

        btnUp.setOnClickListener(vv -> moveKey(gi, ki, -1));
        btnDown.setOnClickListener(vv -> moveKey(gi, ki, +1));
        btnMenu.setOnClickListener(vv -> showKeyMenu(vv, gi, ki));
        v.setOnLongClickListener(vv -> {
            showKeyMenu(btnMenu, gi, ki);
            return true;
        });
        return v;
    }

    // ------------------------------------------------------------------
    // 数据操作
    // ------------------------------------------------------------------

    private void rebuildRows() {
        rows.clear();
        for (int gi = 0; gi < groups.size(); gi++) {
            KeyGroup g = groups.get(gi);
            rows.add(new Row(TYPE_GROUP, gi, -1, g, null));
            for (int ki = 0; ki < g.keys.size(); ki++) {
                rows.add(new Row(TYPE_KEY, gi, ki, g, g.keys.get(ki)));
            }
        }
    }

    private void save() {
        // 同步每个按键的 groupName（防止重命名后不一致）
        for (KeyGroup g : groups) {
            for (int i = 0; i < g.keys.size(); i++) {
                CustomKey old = g.keys.get(i);
                if (!old.groupName.equals(g.name)) {
                    g.keys.set(i, new CustomKey(old.text, old.autoEnter, g.name));
                }
            }
        }
        KeyStore.saveGroups(this, groups);
        rebuildRows();
        refresh();
    }

    private void refresh() {
        adapter.notifyDataSetChanged();
        // 空判定：没有按键即视为空（即便有分组标题）
        boolean noKeys = true;
        for (KeyGroup g : groups) if (!g.keys.isEmpty()) { noKeys = false; break; }
        tvEmpty.setVisibility(noKeys ? View.VISIBLE : View.GONE);
    }

    private void moveKey(int gi, int ki, int delta) {
        KeyGroup g = groups.get(gi);
        int to = ki + delta;
        if (to < 0 || to >= g.keys.size()) return;
        CustomKey k = g.keys.remove(ki);
        g.keys.add(to, k);
        save();
    }

    private void moveKeyToGroup(int gi, int ki, int targetGi) {
        if (gi == targetGi) return;
        KeyGroup src = groups.get(gi);
        KeyGroup dst = groups.get(targetGi);
        CustomKey k = src.keys.remove(ki);
        // 重建以更新 groupName
        dst.keys.add(new CustomKey(k.text, k.autoEnter, dst.name));
        save();
    }

    // ------------------------------------------------------------------
    // 对话框
    // ------------------------------------------------------------------

    /** gi/ki 都为 -1 表示新建（归入「默认」分组）。 */
    private void showKeyDialog(final int gi, final int ki) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad, pad, 0);

        final EditText et = new EditText(this);
        et.setHint("按键输入的内容，例如：12");
        if (gi >= 0 && ki >= 0) et.setText(groups.get(gi).keys.get(ki).text);
        box.addView(et);

        final CheckBox cb = new CheckBox(this);
        cb.setText("输入后自动按回车（自动发送）");
        if (gi >= 0 && ki >= 0) cb.setChecked(groups.get(gi).keys.get(ki).autoEnter);
        else cb.setChecked(true);
        box.addView(cb);

        final int targetGroupIdx;
        if (gi >= 0) targetGroupIdx = gi;
        else targetGroupIdx = 0; // 新建默认归入第一个分组

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(gi >= 0 ? "编辑按键" : "新建按键")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String t = et.getText().toString();
                    if (t.isEmpty()) {
                        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CustomKey nk = new CustomKey(t, cb.isChecked(),
                            groups.get(targetGroupIdx).name);
                    if (gi >= 0 && ki >= 0) {
                        groups.get(gi).keys.set(ki, nk);
                    } else {
                        groups.get(targetGroupIdx).keys.add(nk);
                    }
                    save();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null);
        builder.show();
    }

    private void showNewGroupDialog() {
        final EditText et = new EditText(this);
        et.setHint("分组名称");
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("新建分组")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    String name = et.getText().toString();
                    if (!KeyStore.addGroup(groups, name)) {
                        Toast.makeText(this, "名称无效或已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        save();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameGroupDialog(final int gi) {
        final EditText et = new EditText(this);
        et.setText(groups.get(gi).name);
        et.setSelection(et.getText().length());
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);
        box.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("重命名分组")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String n = et.getText().toString();
                    // 因为 groupName 同步在 save() 里完成，这里只更新 group.name
                    if (!KeyStore.renameGroup(groups, gi, n)) {
                        Toast.makeText(this, "名称无效或已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        save();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteGroupConfirm(final int gi) {
        KeyGroup g = groups.get(gi);
        boolean isDefault = g.name.equals(KeyStore.DEFAULT_GROUP);
        int keysCount = g.keys.size();
        String msg;
        if (isDefault && keysCount > 0) {
            msg = "「默认」分组包含 " + keysCount + " 个按键，删除后按键也会一并删除。\n确定吗？";
        } else if (keysCount > 0) {
            msg = "删除分组「" + g.name + "」及其 " + keysCount + " 个按键？";
        } else {
            msg = "删除空分组「" + g.name + "」？";
        }
        new AlertDialog.Builder(this)
                .setTitle("删除分组")
                .setMessage(msg)
                .setPositiveButton("删除", (d, w) -> {
                    groups.remove(gi);
                    if (groups.isEmpty()) groups.add(new KeyGroup(KeyStore.DEFAULT_GROUP));
                    save();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ------------------------------------------------------------------
    // PopupMenu
    // ------------------------------------------------------------------

    private void showGroupMenu(View anchor, final int gi) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "新建按键到本组");
        pm.getMenu().add(0, 2, 1, "重命名分组");
        pm.getMenu().add(0, 3, 2, "删除分组");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showKeyDialog(gi, -1); return true;
                case 2: renameGroupDialog(gi); return true;
                case 3: deleteGroupConfirm(gi); return true;
            }
            return false;
        });
        pm.show();
    }

    private void showKeyMenu(View anchor, final int gi, final int ki) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "编辑");
        // 「移动到分组」子菜单：列出所有分组（排除当前）+ 新建分组
        android.view.SubMenu sm = pm.getMenu().addSubMenu(0, 0, 1, "移动到分组");
        int baseId = 100;
        for (int i = 0; i < groups.size(); i++) {
            if (i == gi) continue;
            sm.add(0, baseId + i, i, groups.get(i).name);
        }
        sm.add(0, baseId + groups.size(), groups.size(), "+ 新分组…");
        pm.getMenu().add(0, 2, 2, "删除");
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) { showKeyDialog(gi, ki); return true; }
            if (id == 2) {
                groups.get(gi).keys.remove(ki);
                save();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                return true;
            }
            if (id >= baseId && id < baseId + groups.size()) {
                moveKeyToGroup(gi, ki, id - baseId);
                return true;
            }
            if (id == baseId + groups.size()) {
                final EditText et = new EditText(this);
                et.setHint("新分组名称");
                int pad = dp(20);
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(pad, pad, pad, 0);
                box.addView(et);
                new AlertDialog.Builder(this)
                        .setTitle("新建分组并移入")
                        .setPositiveButton("确定", (d, w) -> {
                            String n = et.getText().toString();
                            if (!KeyStore.addGroup(groups, n)) {
                                Toast.makeText(this, "名称无效或已存在", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            int newIdx = groups.size() - 1;
                            moveKeyToGroup(gi, ki, newIdx);
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            }
            return false;
        });
        pm.show();
    }

    // ------------------------------------------------------------------
    // 输入法状态
    // ------------------------------------------------------------------

    private void updateImeStatus() {
        InputMethodManager imm = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        String imeId = new ComponentName(this, ImeService.class).flattenToString();

        boolean enabled = false;
        try {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
                if (info.getId().equals(imeId)) { enabled = true; break; }
            }
        } catch (Throwable ignored) {}
        boolean current = false;
        try {
            current = imeId.equals(Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD));
        } catch (Throwable ignored) {}

        if (current) {
            tvImeStatus.setText("✅ 已启用并设为当前输入法\n去任意聊天框试试：点键盘顶部的自定义按键即可自动输入并发送");
        } else if (enabled) {
            tvImeStatus.setText("⚠️ 已启用，但还不是当前输入法\n点击「切换为当前输入法」，在弹出的列表里选择「自定义按键输入法」");
        } else {
            tvImeStatus.setText("❌ 尚未启用\n1. 点击「去系统设置启用本输入法」\n2. 找到 语言和输入法 → 虚拟键盘 / 管理键盘 / 可用输入法\n3. 打开「自定义按键输入法」的开关\n4. 返回本页，点「切换为当前输入法」");
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}