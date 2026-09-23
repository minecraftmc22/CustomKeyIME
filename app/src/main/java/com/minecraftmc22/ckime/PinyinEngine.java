package com.minecraftmc22.ckime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 简版拼音引擎。
 *
 * 词典来自 assets/pinyin.txt，格式为「pinyin \t 候选词1 候选词2 ...」（候选词按词频降序）。
 *
 * 候选策略（按优先级）：
 *   1. 整串精确匹配              nihao    -> 你好
 *   2. 全串切分（最长匹配优先）   woxihuanni -> 我 + 喜欢 + 你
 *   3. 前缀匹配（边打边提示）     nih      -> 你好 / 你 ...
 *
 * 多音字：单字已收录全部读音；词语读音由 pypinyin 的词组规则生成（如 银行 -> yinhang）。
 */
public class PinyinEngine {

    private static PinyinEngine sInstance;
    private static final Object LOCK = new Object();

    private final TreeMap<String, String[]> dict = new TreeMap<>();
    private int maxKeyLen = 1;
    private boolean[] canFinish;   // 切分 DP 可行性

    public static PinyinEngine get(Context ctx) {
        PinyinEngine e = sInstance;
        if (e == null) {
            synchronized (LOCK) {
                e = sInstance;
                if (e == null) {
                    e = new PinyinEngine(ctx.getApplicationContext());
                    sInstance = e;
                }
            }
        }
        return e;
    }

    /** 后台预加载词库，避免首次输入时卡顿。 */
    public static void preload(Context ctx) {
        if (sInstance != null) return;
        new Thread(() -> get(ctx), "pinyin-preload").start();
    }

    private PinyinEngine(Context ctx) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open("pinyin.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String py = line.substring(0, tab);
                String rest = line.substring(tab + 1).trim();
                if (rest.isEmpty()) continue;
                String[] words = rest.split(" ");
                dict.put(py, words);
                if (py.length() > maxKeyLen) maxKeyLen = py.length();
            }
            br.close();
        } catch (Exception ignored) {
            // 载入失败：引擎不可用，输入法退化为纯英文输入
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isReady() {
        return !dict.isEmpty();
    }

    /**
     * 查询候选词。
     * @param input 用户输入的拼音串（纯小写字母）
     * @param max   最多返回多少个
     */
    public List<String> candidates(String input, int max) {
        List<String> out = new ArrayList<>();
        if (input == null || input.isEmpty() || dict.isEmpty()) return out;

        LinkedHashSet<String> set = new LinkedHashSet<>();

        // 1) 精确匹配
        String[] exact = dict.get(input);
        if (exact != null) {
            for (String w : exact) {
                set.add(w);
                if (set.size() >= max) break;
            }
        }

        // 2) 全串切分（仅当整串能被完整切分时）
        if (set.size() < max) {
            computeCanFinish(input);
            if (canFinish[0]) {
                segment(input, 0, "", set, max, 0);
            }
        }

        // 3) 前缀匹配（边打边提示）
        if (set.size() < max) {
            for (Map.Entry<String, String[]> e : dict.tailMap(input).entrySet()) {
                String key = e.getKey();
                if (!key.startsWith(input)) break;
                if (key.equals(input)) continue;
                for (String w : e.getValue()) {
                    set.add(w);
                    if (set.size() >= max) break;
                }
                if (set.size() >= max) break;
            }
        }

        out.addAll(set);
        if (out.size() > max) return out.subList(0, max);
        return out;
    }

    // ------------------------------------------------------------------

    /** DP：canFinish[i] = 输入串的 s[i..] 部分能否被完整切分成词。 */
    private void computeCanFinish(String s) {
        int n = s.length();
        canFinish = new boolean[n + 1];
        canFinish[n] = true;
        for (int i = n - 1; i >= 0; i--) {
            int end = Math.min(n, i + maxKeyLen);
            for (int e = end; e > i; e--) {
                if (canFinish[e] && dict.containsKey(s.substring(i, e))) {
                    canFinish[i] = true;
                    break;
                }
            }
        }
    }

    private void segment(String s, int pos, String acc,
                         LinkedHashSet<String> out, int max, int depth) {
        if (out.size() >= max || depth > 6) return;
        if (pos >= s.length()) {
            if (!acc.isEmpty()) out.add(acc);
            return;
        }
        int end = Math.min(s.length(), pos + maxKeyLen);
        // 最长匹配优先，让「woxihuanni -> 我喜欢你」这类切分排前面
        for (int e = end; e > pos; e--) {
            String key = s.substring(pos, e);
            String[] words = dict.get(key);
            if (words == null) continue;
            if (e < s.length() && !canFinish[e]) continue;   // 剪枝
            int tried = 0;
            for (String w : words) {
                if (tried >= 3) break;
                segment(s, e, acc + w, out, max, depth + 1);
                tried++;
                if (out.size() >= max) return;
            }
        }
    }
}
