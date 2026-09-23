package com.minecraftmc22.ckime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 输入记忆：记录用户经常输入的内容，按使用次数排序（次数相同取最近）。
 * 用于「常用/记忆」面板，点击即可再次输入。
 */
public class MemoryStore {

    private static final String PREFS = "memory_store";
    private static final String KEY = "items";
    public static final int MAX = 100;

    public static class Item {
        public final String text;
        public int count;

        Item(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    public static List<Item> load(Context c) {
        List<Item> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Item(o.optString("t"), o.optInt("c")));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    /** 记录一次输入，次数 +1（不存在则新增）。 */
    public static void record(Context c, String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty() || t.length() > 60) return;
        List<Item> list = load(c);
        boolean found = false;
        for (Item it : list) {
            if (it.text.equals(t)) {
                it.count++;
                found = true;
                break;
            }
        }
        if (!found) list.add(0, new Item(t, 1));
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return b.count - a.count;
            }
        });
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(c, list);
    }

    public static void remove(Context c, String text) {
        List<Item> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).text.equals(text)) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    public static void clear(Context c) {
        save(c, new ArrayList<Item>());
    }

    private static void save(Context c, List<Item> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("t", it.text);
                o.put("c", it.count);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString(KEY, arr.toString()).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
