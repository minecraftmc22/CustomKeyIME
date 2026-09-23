package com.minecraftmc22.ckime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义按键输入法（Material You 3 主题）。
 *
 * 4 个标签页：
 *   键盘 / 自定义按键 / 剪贴板 / 记忆
 *
 * 功能：
 *   - 普通键盘：QWERTY + 中英切换（内置简版拼音）
 *     · 中文：支持首字母简拼联想（nh -> 你好）；回车直接上屏所打字母
 *     · 英文：边打边出词语联想（he -> hello / help ...）
 *   - 自定义按键：整屏网格，一点输入并可自动回车发送
 *   - 剪贴板：历史记录 + 复制当前选中 + 点击粘贴
 *   - 输入记忆：按频率记录常用内容，点击再次输入
 *   - 退格：长按快速删除；长按后上滑松手 = 删除全部文本
 *   - 键盘高度 / 背景透明度 / 明暗：设置里可调，即时生效
 */
public class ImeService extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int MODE_NORMAL = 0;   // 键盘
    private static final int MODE_KEYS = 1;     // 自定义按键
    private static final int MODE_CLIP = 2;     // 剪贴板
    private static final int MODE_MEMORY = 3;   // 输入记忆

    private static final String STATE_PREFS = "ime_state";
    private static final String K_MODE = "mode";
    private static final String K_CHINESE = "chinese";

    private boolean caps = false;
    private boolean symbolMode = false;
    private boolean chinese = true;
    private int mode = MODE_NORMAL;

    // 拼音输入
    private String pinyinBuffer = "";
    private List<String> candidates = new ArrayList<>();
    private LinearLayout candidateBox;

    // 英文输入（联想用：跟踪当前已上屏的词内字母）
    private String englishBuffer = "";

    // 退格长按 / 上滑删除全部
    private final Handler handler = new Handler();
    private boolean bsLong = false;
    private boolean bsSwiped = false;
    private float bsDownY = 0;
    private static final int LONG_PRESS_MS = 400;
    private final Runnable repeatDelete = new Runnable() {
        @Override public void run() {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            handler.postDelayed(this, 45);
        }
    };

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.init(this);

        SharedPreferences sp = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        mode = sp.getInt(K_MODE, MODE_NORMAL);
        chinese = sp.getBoolean(K_CHINESE, true);

        getSharedPreferences(KeyStore.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);
        getSharedPreferences(AppSettings.PREFS, MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);

        // 监听系统剪贴板变化（IME 活跃期间），写入历史
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.addPrimaryClipChangedListener(clipListener);
    }

    @Override
    public void onDestroy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        return buildKeyboard();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        resetPinyin();
        // 确保高度/背景/主题等设置变更后立即生效
        rebuild();
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        flushPinyin();
        super.onFinishInputView(finishingInput);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (isInputViewShown()) rebuild();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    private void saveState() {
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putInt(K_MODE, mode)
                .putBoolean(K_CHINESE, chinese)
                .apply();
    }

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
        // 背景：自定义图片（若有）或主题纯色，均受透明度 / 明暗设置影响
        try {
            root.setBackground(ThemeHelper.keyboardBackground(this,
                    AppSettings.getBgAlpha(this), AppSettings.getBgBrightness(this)));
        } catch (Throwable t) {
            root.setBackgroundColor(ThemeHelper.background(
                    AppSettings.getBgAlpha(this), AppSettings.getBgBrightness(this)));
        }
        root.setPadding(dp(4), dp(4), dp(4), dp(6));

        root.addView(buildTabRow(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (mode == MODE_KEYS) {
            root.addView(buildCustomGrid(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(buildCustomBottomRow(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else if (mode == MODE_CLIP) {
            root.addView(buildClipPanel(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else if (mode == MODE_MEMORY) {
            root.addView(buildMemoryPanel(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        } else {
            if (!symbolMode) {
                root.addView(buildCandidateBar(), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            buildNormalRows(root);
        }
        return root;
    }

    /** 顶部标签页 + 设置入口 */
    private View buildTabRow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        String[] labels = {"键盘", "自定义", "剪贴板", "记忆"};
        int[] modes = {MODE_NORMAL, MODE_KEYS, MODE_CLIP, MODE_MEMORY};
        for (int i = 0; i < labels.length; i++) {
            final int m = modes[i];
            Button t = tabButton(labels[i], mode == m);
            t.setOnClickListener(v -> {
                if (mode == m) return;
                if (mode == MODE_NORMAL) flushPinyin();
                mode = m;
                saveState();
                rebuild();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(4));
            bar.addView(t, lp);
        }

        Button gear = tabButton("⚙", false);
        gear.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(42), dp(38));
        glp.setMargins(dp(2), dp(2), dp(2), dp(4));
        bar.addView(gear, glp);
        return bar;
    }

    private Button tabButton(String label, boolean active) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(active ? ThemeHelper.onSurface : ThemeHelper.onSurfaceDim);
        b.setBackground(rounded(active ? ThemeHelper.accent : ThemeHelper.surfaceVariant, 6));
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    // ------------------------------------------------------------------
    // 候选栏（拼音）
    // ------------------------------------------------------------------

    private View buildCandidateBar() {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        candidateBox = new LinearLayout(this);
        candidateBox.setOrientation(LinearLayout.HORIZONTAL);
        candidateBox.setGravity(Gravity.CENTER_VERTICAL);
        candidateBox.setBackgroundColor(ThemeHelper.surfaceVariant2);
        hsv.addView(candidateBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        updateCandidates();
        return hsv;
    }

    private void updateCandidates() {
        if (candidateBox == null) return;
        candidateBox.removeAllViews();

        final boolean cn = chinese && !symbolMode;
        final String buf = cn ? pinyinBuffer : englishBuffer;

        TextView tv = new TextView(this);
        tv.setText(buf.isEmpty() ? (cn ? "拼音" : "英文") : buf);
        tv.setTextColor(buf.isEmpty() ? ThemeHelper.onSurfaceDim : ThemeHelper.accent);
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(12), 0, dp(12), 0);
        candidateBox.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        candidates.clear();
        if (!buf.isEmpty()) {
            if (cn) {
                candidates = PinyinEngine.get(this).candidates(buf, 24);
            } else {
                candidates = EnglishEngine.get(this).suggest(buf, 24);
            }
        }
        for (String w : candidates) {
            Button b = new Button(this);
            b.setText(w);
            b.setAllCaps(false);
            b.setTextColor(ThemeHelper.onSurface);
            b.setTextSize(17);
            b.setPadding(dp(10), 0, dp(10), 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setBackground(rounded(ThemeHelper.surfaceVariant, 6));
            b.setOnClickListener(v -> pickCandidate(w));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMargins(dp(2), dp(3), dp(2), dp(3));
            candidateBox.addView(b, lp);
        }
    }

    // ------------------------------------------------------------------
    // 普通键盘
    // ------------------------------------------------------------------

    private void buildNormalRows(LinearLayout root) {
        if (symbolMode) {
            String[] s1 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
            String[] s2 = {"@", "#", "$", "%", "&", "-", "+", "(", ")"};
            String[] s3 = {"*", "\"", "'", ":", ";", "!", "?"};
            root.addView(letterRow(s1));
            root.addView(letterRow(s2));
            LinearLayout row3 = row();
            row3.addView(funcKey("abc", v -> { symbolMode = false; rebuild(); }));
            for (String s : s3) row3.addView(letterKey(s));
            row3.addView(backspaceKey());
            root.addView(row3);
        } else {
            String[] r1 = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"};
            String[] r2 = {"a", "s", "d", "f", "g", "h", "j", "k", "l"};
            String[] r3 = {"z", "x", "c", "v", "b", "n", "m"};
            root.addView(letterRow(r1));
            root.addView(letterRow(r2));
            LinearLayout row3 = row();
            row3.addView(funcKey("⇧", v -> { caps = !caps; rebuild(); }));
            for (String s : r3) row3.addView(letterKey(s));
            row3.addView(funcKey("?123", v -> { flushPinyin(); englishBuffer = ""; symbolMode = true; rebuild(); }));
            row3.addView(backspaceKey());
            root.addView(row3);
        }

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
            pinyinBuffer += ch.toLowerCase();
            if (pinyinBuffer.length() > 24) flushPinyin();
            updateCandidates();
            return;
        }
        commit(ch);
        if (!chinese && !symbolMode) {
            // 英文模式：字母已上屏，同步记录当前词，用于联想
            englishBuffer += ch;
            if (englishBuffer.length() > 24) englishBuffer = "";
            updateCandidates();
        }
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
        if (!chinese && !symbolMode && !englishBuffer.isEmpty()) {
            englishBuffer = englishBuffer.substring(0, englishBuffer.length() - 1);
            updateCandidates();
        }
    }

    /** 退格键：长按快速删除；长按后上滑松手 = 删除全部文本 */
    private Button backspaceKey() {
        Button b = funcKey("⌫", v -> {});
        b.setOnTouchListener(new View.OnTouchListener() {
            final Runnable pending = new Runnable() {
                @Override public void run() {
                    bsLong = true;
                    b.setText("⌫");
                    b.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    handler.removeCallbacks(repeatDelete);
                    handler.post(repeatDelete);
                }
            };
            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        bsLong = false;
                        bsSwiped = false;
                        bsDownY = ev.getRawY();
                        handler.removeCallbacks(pending);
                        handler.postDelayed(pending, LONG_PRESS_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!bsSwiped && (bsDownY - ev.getRawY()) > dp(70)) {
                            bsSwiped = true;
                            b.setText("清空");
                            b.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            handler.removeCallbacks(pending);
                            handler.removeCallbacks(repeatDelete);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(pending);
                        handler.removeCallbacks(repeatDelete);
                        b.setText("⌫");
                        if (bsSwiped) {
                            deleteAllText();
                        } else if (!bsLong) {
                            onBackspace();
                        }
                        bsLong = false;
                        bsSwiped = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(pending);
                        handler.removeCallbacks(repeatDelete);
                        b.setText("⌫");
                        bsLong = false;
                        bsSwiped = false;
                        return true;
                }
                return false;
            }
        });
        return b;
    }

    private void deleteAllText() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(100000, 0);
            CharSequence after = ic.getTextAfterCursor(100000, 0);
            int bl = before == null ? 0 : before.length();
            int al = after == null ? 0 : after.length();
            ic.deleteSurroundingText(bl, al);
            Toast.makeText(this, "已清空全部内容", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            try { ic.deleteSurroundingText(100000, 100000); } catch (Throwable ignored2) {}
        }
    }

    private void onEnter() {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            // 回车 = 上屏输入的原始字母（而不是首选词），随后照常回车
            commitRawPinyin();
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            return;
        }
        if (!chinese && !symbolMode) resetEnglish();
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    private void onPunctuation(String p) {
        if (chinese && !symbolMode && !pinyinBuffer.isEmpty()) {
            commitFirstCandidate();
        }
        commit(p);
        if (!chinese && !symbolMode) resetEnglish();
    }

    private void toggleLanguage() {
        if (pinyinBuffer.isEmpty()) { /* 无待上屏拼音 */ }
        else flushPinyin();
        chinese = !chinese;
        symbolMode = false;
        englishBuffer = "";
        saveState();
        rebuild();
    }

    private void pickCandidate(String w) {
        if (!chinese || symbolMode) {
            pickEnglish(w);
            return;
        }
        commit(w);
        MemoryStore.record(this, w);
        pinyinBuffer = "";
        updateCandidates();
    }

    /** 英文候选：字母已上屏，先删掉已输入的字母再用完整词替换。 */
    private void pickEnglish(String w) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || englishBuffer.isEmpty()) return;
        String out = w;
        if (Character.isUpperCase(englishBuffer.charAt(0))) {
            out = Character.toUpperCase(w.charAt(0)) + w.substring(1);
        }
        ic.deleteSurroundingText(englishBuffer.length(), 0);
        ic.commitText(out, 1);
        MemoryStore.record(this, out);
        resetEnglish();
    }

    private void resetEnglish() {
        englishBuffer = "";
        updateCandidates();
    }

    private void commitFirstCandidate() {
        if (pinyinBuffer.isEmpty()) return;
        String out = !candidates.isEmpty() ? candidates.get(0) : pinyinBuffer;
        commit(out);
        MemoryStore.record(this, out);
        pinyinBuffer = "";
        updateCandidates();
    }

    /** 上屏当前拼音缓冲的原始字母（不做转换）。 */
    private void commitRawPinyin() {
        if (pinyinBuffer.isEmpty()) return;
        String out = caps ? pinyinBuffer.toUpperCase() : pinyinBuffer;
        commit(out);
        pinyinBuffer = "";
        updateCandidates();
    }

    /** 切换标签/结束输入时：把未转换的拼音按原样上屏，避免用户没选的词被强行打出。 */
    private void flushPinyin() {
        if (!pinyinBuffer.isEmpty()) {
            String out = pinyinBuffer;
            commit(out);
            pinyinBuffer = "";
            updateCandidates();
        }
    }

    private void resetPinyin() {
        pinyinBuffer = "";
        englishBuffer = "";
        candidates.clear();
        if (candidateBox != null) updateCandidates();
    }

    // ------------------------------------------------------------------
    // 自定义按键网格
    // ------------------------------------------------------------------

    private View buildCustomGrid() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<KeyGroup> groups = KeyStore.loadGroups(this);
        java.util.Set<String> collapsedNames = KeyStore.loadCollapsed(this);
        boolean any = false;
        boolean first = true;
        for (KeyGroup g : groups) {
            if (g.keys.isEmpty()) continue;
            any = true;
            final String gname = g.name;
            final boolean isCollapsed = collapsedNames.contains(gname);

            // 分组栏：点击折叠 / 展开（状态持久化）
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(6), first ? dp(4) : dp(9), dp(6), dp(3));

            TextView t = new TextView(this);
            t.setText((isCollapsed ? "▸ " : "▾ ") + gname
                    + "  (" + g.keys.size() + (isCollapsed ? " 已折叠)" : ")"));
            t.setTextColor(ThemeHelper.accent);
            t.setTextSize(12);
            t.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(t);

            header.setOnClickListener(v -> {
                KeyStore.setCollapsed(this, gname, !isCollapsed);
                rebuild();
            });
            col.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            first = false;

            if (isCollapsed) continue;   // 折叠的分组不渲染按键

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
            if (row != null) {
                int rem = (4 - (g.keys.size() % 4)) % 4;
                for (int j = 0; j < rem; j++) row.addView(new View(this), gridParams());
            }
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("还没有自定义按键\n点下方「＋ 管理按键」新建");
            empty.setTextColor(ThemeHelper.onSurfaceDim);
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
        b.setTextColor(ThemeHelper.ON_ACCENT);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(rounded(ThemeHelper.accent, 6));
        b.setOnClickListener(v -> fireCustomKey(k));
        return b;
    }

    private LinearLayout.LayoutParams gridParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, keyH(52), 1f);
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

    private void fireCustomKey(CustomKey k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!k.text.isEmpty()) {
            ic.commitText(k.text, 1);
            MemoryStore.record(this, k.text);
        }
        if (k.autoEnter) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    // ------------------------------------------------------------------
    // 剪贴板面板
    // ------------------------------------------------------------------

    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = () -> {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData.Item item = cm.getPrimaryClip().getItemAt(0);
        CharSequence cs = item.coerceToText(this);
        if (cs != null) ClipboardStore.add(this, cs.toString());
    };

    private View buildClipPanel() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = row();
        actions.addView(funcKeyWeight("复制当前选中", v -> copySelection(), 1.6f));
        actions.addView(funcKey("清空", v -> {
            ClipboardStore.clear(this);
            rebuild();
        }));
        col.addView(actions);

        List<String> items = ClipboardStore.load(this);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无剪贴板历史\n复制的内容会出现在这里");
            empty.setTextColor(ThemeHelper.onSurfaceDim);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            col.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        } else {
            for (int i = 0; i < items.size(); i++) {
                final String it = items.get(i);
                final int idx = i;
                Button b = panelItem(it);
                b.setOnClickListener(v -> {
                    commit(it);
                    MemoryStore.record(this, it);
                });
                b.setOnLongClickListener(v -> {
                    ClipboardStore.remove(this, idx);
                    rebuild();
                    return true;
                });
                col.addView(b, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, keyH(42)));
            }
        }
        return sv;
    }

    private void copySelection() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence sel = ic.getSelectedText(0);
        String s = sel == null ? null : sel.toString();
        if (s == null || s.isEmpty()) {
            Toast.makeText(this, "请先在输入框里选中要复制的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ckime", s));
        ClipboardStore.add(this, s);
        rebuild();
    }

    // ------------------------------------------------------------------
    // 输入记忆面板
    // ------------------------------------------------------------------

    private View buildMemoryPanel() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = row();
        actions.addView(funcKey("清空记忆", v -> {
            MemoryStore.clear(this);
            rebuild();
        }));
        col.addView(actions);

        List<MemoryStore.Item> items = MemoryStore.load(this);
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无输入记忆\n你常用的内容会按频率出现在这里");
            empty.setTextColor(ThemeHelper.onSurfaceDim);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            col.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));
        } else {
            for (final MemoryStore.Item it : items) {
                Button b = panelItem(it.text + "   ·" + it.count);
                b.setOnClickListener(v -> commit(it.text));
                b.setOnLongClickListener(v -> {
                    MemoryStore.remove(this, it.text);
                    rebuild();
                    return true;
                });
                col.addView(b, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, keyH(42)));
            }
        }
        return sv;
    }

    private Button panelItem(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(ThemeHelper.onSurface);
        b.setTextSize(14);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setBackground(rounded(ThemeHelper.surfaceVariant, 6));
        return b;
    }

    // ------------------------------------------------------------------
    // 控件工具
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
        b.setTextColor(ThemeHelper.onSurfaceDim);
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
        if (!chinese && !symbolMode) resetEnglish();
    }

    private Button funcKey(String label, View.OnClickListener l) {
        return funcKeyWeight(label, l, 1f);
    }

    private Button funcKeyWeight(String label, View.OnClickListener l, float weight) {
        Button b = makeKey(label);
        b.setBackground(keyBg(ThemeHelper.accentContainer));
        b.setTextColor(ThemeHelper.onSurface);
        b.setLayoutParams(lp(weight));
        b.setOnClickListener(l);
        return b;
    }

    private Button makeKey(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(ThemeHelper.onSurface);
        b.setTextSize(16);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(2), 0, dp(2), 0);
        b.setBackground(keyBg(ThemeHelper.surfaceVariant));
        b.setLayoutParams(lp(1f));
        return b;
    }

    private LinearLayout.LayoutParams lp(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, keyH(48), weight);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        return lp;
    }

    /** 圆角背景（普通） */
    private Drawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return d;
    }

    /** 带按压态的背景 */
    private Drawable keyBg(int color) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed},
                rounded(ThemeHelper.applyBrightness(color, -14), 6));
        sld.addState(new int[]{}, rounded(color, 6));
        return sld;
    }

    private void commit(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    private float hscale() {
        return AppSettings.getKeyHeightPercent(this) / 100f;
    }

    private int keyH(int baseDp) {
        return Math.round(baseDp * hscale() * getResources().getDisplayMetrics().density);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
