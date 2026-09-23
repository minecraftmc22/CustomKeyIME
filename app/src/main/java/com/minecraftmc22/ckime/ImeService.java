package com.minecraftmc22.ckime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义按键输入法。
 *
 * 两种键盘模式（顶部标签页切换）：
 *   1. 「键盘」  —— 普通 QWERTY 键盘，支持 中/英 切换（中文为内置简版拼音）
 *   2. 「自定义」—— 整屏网格，按分组排列所有自定义按键，一点即输入（可选自动回车发送）
 */
public class ImeService extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int MODE_KEYS = 0;     // 自定义按键网格
    private static final int MODE_NORMAL = 1;   // 普通键盘

    private static final String STATE_PREFS = "ime_state";
    private static final String K_MODE = "mode";
    private static final String K_CHINESE = "chinese";

    private boolean caps = false;         // 字母大写
    private boolean symbolMode = false;   // 数字/符号面板
    private boolean chinese = true;       // 中文(true) / 英文(false)
    private int mode = MODE_NORMAL;

    // 拼音输入状态
    private String pinyinBuffer = "";
    private List<String> candidates = new ArrayList<>();
    private LinearLayout candidateBox;

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        mode = sp.getInt(K_MODE, MODE_NORMAL);
        chinese = sp.getBoolean(K_CHINESE, true);

        // 监听按键数据变化：在设置里改完，键盘立即刷新
        getSharedPreferences(KeyStore.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);

        // 后台预加载拼音词库
        PinyinEngine.preload(this);
    }

    @Override
    public View onCreateInputView() {
        return buildKeyboard();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        resetPinyin();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        flushPinyin();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        // 按键/分组有改动：若当前正显示自定义面板，立即重建
        if (mode == MODE_KEYS) rebuild();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // 禁止横屏全屏抽取模式，保证回车事件发到原输入框
        return false;
    }

    private void saveState() {
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putInt(K_MODE, mode)
                .putBoolean(K_CHINESE, chinese)
                .apply();
    }

    /** 重建整个键盘（切换模式/中英/大小写/符号面板时调用）。 */
    private void rebuild() {
        setInputView(buildKeyboard());
    }

    // ------------------------------------------------------------------
    // 键盘构建
    // ------------------------------------------------------------------

    private View buildKeyboard() {
        candidateBox = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF141419);
        root.setPadding(dp(4), dp(4), dp(4), dp(6));

        // 顶部标签页
        root.addView(buildTabRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (mode == MODE_KEYS) {
            root.addView(buildCustomGrid(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(buildCustomBottomRow(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            if (chinese && !symbolMode) {
                root.addView(buildCandidateBar(), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            buildNormalRows(root);
        }
        return root;
    }

    /** 顶部标签页：「自定义」/「键盘」 */
    private View buildTabRow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        Button tabKeys = tabButton("自定义按键", mode == MODE_KEYS);
        tabKeys.setOnClickListener(v -> {
            if (mode == MODE_KEYS) return;
            flushPinyin();
            mode = MODE_KEYS;
            saveState();
            rebuild();
        });

        Button tabKb = tabButton("键盘", mode == MODE_NORMAL);
        tabKb.setOnClickListener(v -> {
            if (mode == MODE_NORMAL) return;
            mode = MODE_NORMAL;
            saveState();
            rebuild();
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp1.setMargins(dp(2), dp(2), dp(2), dp(4));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp2.setMargins(dp(2), dp(2), dp(2), dp(4));

        bar.addView(tabKeys, lp1);
        bar.addView(tabKb, lp2);
        return bar;
    }

    private Button tabButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(active ? 0xFFFFFFFF : 0xFF9AA0A6);
        b.setBackgroundResource(active ? R.drawable.tab_active : R.drawable.tab_inactive);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    /** 中文候选栏：左边显示拼音串，右边是候选词 */
    private View buildCandidateBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        candidateBox = new LinearLayout(this);
        candidateBox.setOrientation(LinearLayout.HORIZONTAL);
        candidateBox.setGravity(Gravity.CENTER_VERTICAL);
        candidateBox.setBackgroundColor(0xFF1C1C22);
        hsv.addView(candidateBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        updateCandidates();
        return hsv;
    }

    private void updateCandidates() {
        if (candidateBox == null) return;
        candidateBox.removeAllViews();

        // 拼音串显示
        TextView tv = new TextView(this);
        tv.setText(pinyinBuffer.isEmpty() ? "拼音" : pinyinBuffer);
        tv.setTextColor(pinyinBuffer.isEmpty() ? 0xFF6B7075 : 0xFF80CBC4);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(12), 0, dp(12), 0);
        candidateBox.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        candidates.clear();
        if (!pinyinBuffer.isEmpty()) {
            candidates = PinyinEngine.get(this).candidates(pinyinBuffer, 24);
        }
        for (String w : candidates) {
            Button b = new Button(this);
            b.setText(w);
            b.setAllCaps(false);
            b.setTextColor(0xFFE8EAED);
            b.setTextSize(17);
            b.setPadding(dp(10), 0, dp(10), 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setBackgroundResource(R.drawable.key_bg_candidate);
            b.setOnClickListener(v -> pickCandidate(w));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMargins(dp(2), dp(3), dp(2), dp(3));
            candidateBox.addView(b, lp);
        }
    }

    // ---------------------- 普通键盘 ----------------------

    private void buildNormalRows(LinearLayout root) {
        if (symbolMode) {
            String[] s1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            String[] s2 = {"@", "#", "$", "%", "&", "-", "+", "(", ")"};
            String[] s3 = {"*", "\"", "'", ":", ";", "!", "?"};

            root.addView(letterRow(s1));
            root.addView(letterRow(s2));

            LinearLayout row3 = row();
            row3.addView(funcKey("abc", v -> {
                symbolMode = false;
                rebuild();
            }));
            for (String s : s3) row3.addView(letterKey(s));
            row3.addView(funcKey("⌫", v -> onBackspace()));
            root.addView(row3);
        } else {
            String[] r1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
            String[] r2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
            String[] r3 = {"z", "x", "c", "v", "b", "n", "m"};

            root.addView(letterRow(r1));
            root.addView(letterRow(r2));

            LinearLayout row3 = row();
            row3.addView(funcKey("⇧", v -> {
                caps = !caps;
                rebuild();
            }));
            for (String s : r3) row3.addView(letterKey(s));
            row3.addView(funcKey("?123", v -> {
                flushPinyin();
                symbolMode = true;
                rebuild();
            }));
            row3.addView(funcKey("⌫", v -> onBackspace()));
            root.addView(row3);
        }

        // 底部行：中/英 + 标点 + 空格 + 回车
        LinearLayout row4 = row();
        row4.addView(funcKey(chinese ? "中" : "英", v -> toggleLanguage()));
        row4.addView(funcKey(chinese ? "，" : ",", v -> onPunctuation(chinese ? "，" : ",")));
        row4.addView(spaceKey());
        row4.addView(funcKey(chinese ? "。" : ".", v -> onPunctuation(chinese ? "。" : ".")));
        row4.addView(funcKey("↵", v -> onEnter()));
        root.addView(row4);
    }

    private Button letterKey(String lower) {
        final String out = (caps && !symbolMode) ? lower.toUpperCase() : lower;
        Button b = makeKey(out);
        b.setOnClickListener(v -> onLetter(out));
        return b;
    }

    private void onLetter(String ch) {
        if (chinese && !symbolMode) {
            // 中文：字母进入拼音缓冲
            pinyinBuffer += ch.toLowerCase();
            if (pinyinBuffer.length() > 24) flushPinyin();
            updateCandidates();
            return;
        }
        commit(ch);
        if (caps && !symbolMode) {
            caps = false;
            rebuild();
        }
    }

    private void onBackspace() {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            pinyinBuffer = pinyinBuffer.substring(0, pinyinBuffer.length() - 1);
            updateCandidates();
            return;
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void onEnter() {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            commitFirstCandidate();
            return;
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private void onPunctuation(String p) {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            commitFirstCandidate();
        }
        commit(p);
    }

    private void toggleLanguage() {
        if (pinyinBuffer.isEmpty()) {
            chinese = !chinese;
        } else {
            commitFirstCandidate();
            chinese = !chinese;
        }
        symbolMode = false;
        saveState();
        rebuild();
    }

    private void pickCandidate(String w) {
        commit(w);
        pinyinBuffer = "";
        updateCandidates();
    }

    /** 把当前拼音缓冲提交掉（优先第一个候选词，否则原样提交拼音）。 */
    private void commitFirstCandidate() {
        if (pinyinBuffer.isEmpty()) return;
        String out;
        if (!candidates.isEmpty()) {
            out = candidates.get(0);
        } else {
            out = pinyinBuffer;
        }
        commit(out);
        pinyinBuffer = "";
        updateCandidates();
    }

    private void flushPinyin() {
        if (!pinyinBuffer.isEmpty()) {
            commitFirstCandidate();
        }
    }

    private void resetPinyin() {
        pinyinBuffer = "";
        candidates.clear();
        if (candidateBox != null) updateCandidates();
    }

    // ---------------------- 自定义按键网格 ----------------------

    private View buildCustomGrid() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<KeyGroup> groups = KeyStore.loadGroups(this);
        boolean any = false;
        boolean first = true;
        for (KeyGroup g : groups) {
            if (g.keys.isEmpty()) continue;
            any = true;

            TextView t = new TextView(this);
            t.setText(g.name);
            t.setTextColor(0xFF80CBC4);
            t.setTextSize(12);
            t.setPadding(dp(6), first ? dp(4) : dp(10), dp(6), dp(2));
            col.addView(t);
            first = false;

            LinearLayout row = null;
            int i = 0;
            for (CustomKey k : g.keys) {
                if (i % 4 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    col.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                row.addView(gridKey(k), gridParams());
                i++;
            }
            // 补齐占位，保持左对齐
            if (row != null) {
                int rem = (4 - (g.keys.size() % 4)) % 4;
                for (int j = 0; j < rem; j++) {
                    View sp = new View(this);
                    row.addView(sp, gridParams());
                }
            }
        }

        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("还没有自定义按键\n点下方「＋ 管理按键」新建");
            empty.setTextColor(0xFF6B7075);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            col.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        }
        return sv;
    }

    private Button gridKey(final CustomKey k) {
        Button b = new Button(this);
        b.setText(k.text + (k.autoEnter ? " ↵" : ""));
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(0xFFE8EAED);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackgroundResource(R.drawable.key_bg_custom);
        b.setOnClickListener(v -> fireCustomKey(k));
        return b;
    }

    private LinearLayout.LayoutParams gridParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    private View buildCustomBottomRow() {
        LinearLayout row = row();
        row.addView(funcKeyWeight("＋ 管理按键", v ->
                startActivity(new Intent(this, KeysActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), 2f));
        row.addView(funcKey("⌫", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)));
        row.addView(funcKey("↵", v -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)));
        return row;
    }

    /**
     * 核心：输入自定义内容，然后（可选）自动按回车。
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

    private Button spaceKey() {
        Button b = makeKey("空格");
        b.setTextColor(0xFF9AA0A6);
        b.setTextSize(14);
        b.setOnClickListener(v -> onSpace());
        return b;
    }

    private void onSpace() {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            commitFirstCandidate();
            return;
        }
        commit(" ");
    }

    private Button funcKey(String label, View.OnClickListener l) {
        return funcKeyWeight(label, l, 1f);
    }

    private Button funcKeyWeight(String label, View.OnClickListener l, float weight) {
        Button b = makeKey(label);
        b.setBackgroundResource(R.drawable.key_bg_func);
        b.setTextColor(0xFFFFFFFF);
        b.setLayoutParams(lp(weight));
        b.setOnClickListener(l);
        return b;
    }

    private Button makeKey(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFE8EAED);
        b.setTextSize(16);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackgroundResource(R.drawable.key_bg);
        b.setLayoutParams(lp(1f));
        return b;
    }

    private LinearLayout.LayoutParams lp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), weight);
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
