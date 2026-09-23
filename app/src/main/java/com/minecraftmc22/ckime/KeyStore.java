package com.minecraftmc22.ckime;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自定义按键的持久化存储：SharedPreferences + JSON。
 *
 * 数据结构（v2，支持分组）：
 *   { "groups": [
 *       { "name": "默认", "keys": [ {"text":"12","autoEnter":true}, ... ] },
 *       { "name": "工作", "keys": [...] }
 *   ] }
 *
 * 同时兼容老版本（v1，扁平数组），加载时自动迁移到「默认」分组。
 */
public class KeyStore {

    public static final String PREFS = "custom_keys_prefs";
    private static final String JSON = "keys_json";
    private static final String K_COLLAPSED = "collapsed_groups";
    public static final String DEFAULT_GROUP = "默认";

    /** 加载所有分组（含按键），保留顺序。自动处理老格式迁移。 */
    public static List<KeyGroup> loadGroups(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(JSON, "");
        List<KeyGroup> groups = new ArrayList<>();
        if (!raw.isEmpty()) {
            try {
                // 新格式：以 { 开头；老格式：以 [ 开头
                if (raw.startsWith("{")) {
                    JSONObject root = new JSONObject(raw);
                    JSONArray ga = root.optJSONArray("groups");
                    if (ga != null) {
                        for (int i = 0; i < ga.length(); i++) {
                            JSONObject go = ga.getJSONObject(i);
                            String gname = go.optString("name", DEFAULT_GROUP);
                            KeyGroup kg = new KeyGroup(gname);
                            JSONArray ka = go.optJSONArray("keys");
                            if (ka != null) {
                                for (int j = 0; j < ka.length(); j++) {
                                    JSONObject ko = ka.getJSONObject(j);
                                    kg.keys.add(new CustomKey(
                                            ko.optString("text"),
                                            ko.optBoolean("autoEnter", true),
                                            gname));
                                }
                            }
                            groups.add(kg);
                        }
                    }
                } else if (raw.startsWith("[")) {
                    // 老格式：扁平数组 → 全部归入「默认」分组
                    JSONArray arr = new JSONArray(raw);
                    KeyGroup def = new KeyGroup(DEFAULT_GROUP);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        def.keys.add(new CustomKey(
                                o.optString("text"),
                                o.optBoolean("autoEnter", true),
                                DEFAULT_GROUP));
                    }
                    if (!def.keys.isEmpty()) groups.add(def);
                }
            } catch (JSONException ignored) {
            }
        }
        // 保证至少存在「默认」分组
        if (groups.isEmpty()) {
            groups.add(new KeyGroup(DEFAULT_GROUP));
        }
        return groups;
    }

    /** 扁平加载所有按键（供输入法按键盘上显示，按分组顺序拼接）。 */
    public static List<CustomKey> load(Context ctx) {
        List<CustomKey> all = new ArrayList<>();
        for (KeyGroup g : loadGroups(ctx)) all.addAll(g.keys);
        return all;
    }

    /** 保存完整分组结构。 */
    public static void saveGroups(Context ctx, List<KeyGroup> groups) {
        JSONObject root = new JSONObject();
        JSONArray ga = new JSONArray();
        try {
            for (KeyGroup g : groups) {
                JSONObject go = new JSONObject();
                go.put("name", g.name);
                JSONArray ka = new JSONArray();
                for (CustomKey k : g.keys) {
                    JSONObject ko = new JSONObject();
                    ko.put("text", k.text);
                    ko.put("autoEnter", k.autoEnter);
                    ka.put(ko);
                }
                go.put("keys", ka);
                ga.put(go);
            }
            root.put("groups", ga);
        } catch (JSONException ignored) {
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(JSON, root.toString())
                .apply();
    }

    /** 兼容旧接口：保存扁平按键列表（自动归入默认分组）。 */
    public static void save(Context ctx, List<CustomKey> list) {
        List<KeyGroup> groups = new ArrayList<>();
        KeyGroup def = new KeyGroup(DEFAULT_GROUP);
        def.keys.addAll(list);
        groups.add(def);
        saveGroups(ctx, groups);
    }

    /** 创建分组（去重 + 跳过空名）。同名则返回 null 并提示。 */
    public static boolean addGroup(List<KeyGroup> groups, String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.isEmpty()) return false;
        for (KeyGroup g : groups) {
            if (g.name.equals(n)) return false;
        }
        groups.add(new KeyGroup(n));
        return true;
    }

    /** 重命名分组（同名返回 false）。 */
    public static boolean renameGroup(List<KeyGroup> groups, int index, String newName) {
        if (newName == null) return false;
        String n = newName.trim();
        if (n.isEmpty()) return false;
        for (int i = 0; i < groups.size(); i++) {
            if (i != index && groups.get(i).name.equals(n)) return false;
        }
        KeyGroup kg = groups.get(index);
        kg.name = n;
        // 同步更新分组内按键的 groupName
        for (CustomKey k : kg.keys) {
            // CustomKey.groupName 是 final，重建
            // 这里直接替换 list 里的对象
            // (KeyStore 不持有引用，调用方需整体保存)
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 分组折叠状态（按分组名记录，独立于分组数据）
    // ------------------------------------------------------------------

    /** 读取所有「已折叠」的分组名。 */
    public static Set<String> loadCollapsed(Context ctx) {
        Set<String> set = new HashSet<>();
        String raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(K_COLLAPSED, "");
        if (!raw.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    String n = arr.optString(i, "");
                    if (!n.isEmpty()) set.add(n);
                }
            } catch (JSONException ignored) {
            }
        }
        return set;
    }

    public static boolean isCollapsed(Context ctx, String groupName) {
        return loadCollapsed(ctx).contains(groupName);
    }

    /** 设置某个分组的折叠状态。 */
    public static void setCollapsed(Context ctx, String groupName, boolean collapsed) {
        if (groupName == null) return;
        Set<String> set = loadCollapsed(ctx);
        if (collapsed) set.add(groupName);
        else set.remove(groupName);
        saveCollapsed(ctx, set);
    }

    public static void saveCollapsed(Context ctx, Set<String> set) {
        JSONArray arr = new JSONArray();
        for (String s : set) arr.put(s);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(K_COLLAPSED, arr.toString())
                .apply();
    }

    /** 分组重命名 / 删除后同步折叠状态，避免残留。 */
    public static void renameCollapsed(Context ctx, String oldName, String newName) {
        Set<String> set = loadCollapsed(ctx);
        if (set.remove(oldName)) {
            set.add(newName);
            saveCollapsed(ctx, set);
        }
    }

    public static void removeCollapsed(Context ctx, String groupName) {
        Set<String> set = loadCollapsed(ctx);
        if (set.remove(groupName)) saveCollapsed(ctx, set);
    }
}