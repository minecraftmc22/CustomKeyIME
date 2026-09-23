package com.minecraftmc22.ckime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 简版英文联想引擎。
 *
 * 词表来自 assets/english_words.txt：每行一个词，已按词频降序排列。
 * 联想策略：前缀匹配（输入 he -> hello / help / hear ...），按词频排序。
 *
 * 词表为纯小写；查询时把用户输入转小写后匹配。
 */
public class EnglishEngine {

    private static volatile EnglishEngine sInstance;
    private static final Object LOCK = new Object();

    private final List<String> words = new ArrayList<>();

    public static EnglishEngine get(Context ctx) {
        EnglishEngine e = sInstance;
        if (e == null) {
            synchronized (LOCK) {
                e = sInstance;
                if (e == null) {
                    e = new EnglishEngine(ctx.getApplicationContext());
                    sInstance = e;
                }
            }
        }
        return e;
    }

    private EnglishEngine(Context ctx) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open("english_words.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) words.add(line);
            }
            br.close();
        } catch (Exception ignored) {
            // 载入失败：英文联想不可用，退化为无联想
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    public boolean isReady() {
        return !words.isEmpty();
    }

    /**
     * 前缀联想。
     * @param prefix 用户已输入的字母串（任意大小写）
     * @param max    最多返回多少个
     * @return 以 prefix 开头、且比 prefix 更长的词（词频降序）
     */
    public List<String> suggest(String prefix, int max) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.isEmpty() || words.isEmpty()) return out;
        String p = prefix.toLowerCase();
        for (String w : words) {
            if (w.length() > p.length() && w.startsWith(p)) {
                out.add(w);
                if (out.size() >= max) break;
            }
        }
        return out;
    }
}
