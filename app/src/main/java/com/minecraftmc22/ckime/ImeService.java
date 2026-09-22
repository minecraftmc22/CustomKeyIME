package com.minecraftmc22.ckime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import java.util.List;

/**
 * 自定义按键输入法。
 *
 * 核心功能：键盘顶部有一行可横向滚动的「自定义按键」。
 * 点击自定义按键后：
 *   1. 立即向当前输入框 commitText(自定义内容)
 *   2. 若开启了「自动回车」，紧接着发送 KEYCODE_ENTER（等同于手动按下回车，聊天类 App 会直接发送）
 */
public class ImeService extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private boolean caps = false;       // 大写锁定
    private boolean symbolMode = false; // 符号/数字面板
    private LinearLayout customRow;     // 顶部自定义按键行

    @Override
    public void onCreate() {
        super.onCreate();
        // 监听设置界面保存的数据，保存后键盘立即刷新
        getSharedPreferences(KeyStore.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateInputView() {
        View v = buildKeyboard();
        populateCustomRow();
        return v;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // 每次弹出键盘时刷新一次自定义按键（例如刚在设置里新增了按键）
        populateCustomRow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        populateCustomRow();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // 禁止横屏全屏抽取模式，保证回车事件发到原输入框
        return false;
    }

    // ------------------------------------------------------------------
    // 键盘构建
    // ------------------------------------------------------------------

    private View buildKeyboard() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF141419);
        root.setPadding(dp(4), dp(6), dp(4), dp(8));

        // ---------- 顶部：自定义按键行（可横向滚动） ----------
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(customRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(hsv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (symbolMode) {
            // ---------- 符号 / 数字面板 ----------
            String[] s1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            String[] s2 = {"@", "#", "$", "%", "&", "-", "+", "(", ")"};
            String[] s3 = {"*", "\"", "'", ":", ";", "!", "?"};

            root.addView(letterRow(s1));
            root.addView(letterRow(s2));

            LinearLayout row3 = row();
            row3.addView(funcKey("abc", v -> {
                symbolMode = false;
                setInputView(buildKeyboard());
                populateCustomRow();
            }));
            for (String s : s3) row3.addView(letterKey(s));
            row3.addView(funcKey("⌫", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)));
            root.addView(row3);
        } else {
            // ---------- 字母面板 ----------
            String[] r1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
            String[] r2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
            String[] r3 = {"z", "x", "c", "v", "b", "n", "m"};

            root.addView(letterRow(r1));
            root.addView(letterRow(r2));

            LinearLayout row3 = row();
            row3.addView(funcKey("⇧", v -> {
                caps = !caps;
                setInputView(buildKeyboard());
                populateCustomRow();
            }));
            for (String s : r3) row3.addView(letterKey(s));
            row3.addView(funcKey("?123", v -> {
                symbolMode = true;
                setInputView(buildKeyboard());
                populateCustomRow();
            }));
            row3.addView(funcKey("⌫", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)));
            root.addView(row3);
        }

        // ---------- 底部行：中英文标点 + 空格 + 回车 ----------
        LinearLayout row4 = row();
        row4.addView(funcKey("，", v -> commit("，")));
        row4.addView(spaceKey());
        row4.addView(funcKey("。", v -> commit("。")));
        row4.addView(funcKey("↵", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)));
        root.addView(row4);

        return root;
    }

    /** 填充顶部自定义按键行 */
    private void populateCustomRow() {
        if (customRow == null) return;
        customRow.removeAllViews();

        // 收起键盘
        Button hide = makeKey("▼");
        hide.setBackgroundResource(R.drawable.key_bg_func);
        hide.setOnClickListener(v -> requestHideSelf(0));
        customRow.addView(hide, wrapParams());

        // 所有自定义按键
        List<CustomKey> keys = KeyStore.load(this);
        for (CustomKey k : keys) {
            Button b = makeKey(k.text + (k.autoEnter ? " ↵" : ""));
            b.setBackgroundResource(R.drawable.key_bg_custom);
            b.setSingleLine(true);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setOnClickListener(v -> fireCustomKey(k));
            customRow.addView(b, wrapParams());
        }

        // 打开设置
        Button add = makeKey("＋ 设置");
        add.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        customRow.addView(add, wrapParams());
    }

    /**
     * 核心：输入自定义内容，然后自动按回车。
     */
    private void fireCustomKey(CustomKey k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!k.text.isEmpty()) {
            ic.commitText(k.text, 1);
        }
        if (k.autoEnter) {
            // 等同于用户按下回车键；在微信/QQ/聊天框里即触发「发送」
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    // ------------------------------------------------------------------
    // 控件工具方法
    // ------------------------------------------------------------------

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout letterRow(String[] letters) {
        LinearLayout l = row();
        for (String s : letters) l.addView(letterKey(s));
        return l;
    }

    private Button letterKey(String lower) {
        final String out = (caps && !symbolMode) ? lower.toUpperCase() : lower;
        Button b = makeKey(out);
        b.setOnClickListener(v -> {
            commit(out);
            if (caps && !symbolMode) {
                caps = false;
                setInputView(buildKeyboard());
                populateCustomRow();
            }
        });
        return b;
    }

    private Button spaceKey() {
        Button b = makeKey("空格");
        b.setTextColor(0xFF9AA0A6);
        b.setTextSize(14);
        LinearLayout.LayoutParams lp = lp(4f);
        b.setOnClickListener(v -> commit(" "));
        return b;
    }

    private Button funcKey(String label, View.OnClickListener l) {
        Button b = makeKey(label);
        b.setBackgroundResource(R.drawable.key_bg_func);
        b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(l);
        return b;
    }

    private Button makeKey(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFE8EAED);
        b.setTextSize(18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackgroundResource(R.drawable.key_bg);
        b.setLayoutParams(lp(1f));
        return b;
    }

    private LinearLayout.LayoutParams lp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), weight);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private LinearLayout.LayoutParams wrapParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48));
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
