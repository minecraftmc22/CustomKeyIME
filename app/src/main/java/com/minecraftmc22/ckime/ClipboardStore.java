package com.minecraftmc22.ckime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * 剪贴板历史（应用内自维护，SharedPreferences + JSON）。
 * 最新在前，最多 MAX 条，自动去重。
 */
public class ClipboardStore {

    private static final String PREFS = "clipboard_store";
    private static final String KEY = "items";
    public static final int MAX = 30;

    public static List<String> load(Context c) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void add(Context c, String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        List<String> list = load(c);
        list.remove(t);          // 去重
        list.add(0, t);          // 最新在前
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(c, list);
    }

    public static void remove(Context c, int index) {
        List<String> list = load(c);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(c, list);
        }
    }

    public static void clear(Context c) {
        save(c, new ArrayList<String>());
    }

    private static void save(Context c, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
