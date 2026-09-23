package com.minecraftmc22.ckime;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 自定义按键导出。
 *
 * 两种格式：
 *   - JSON：完整备份（分组 + 按键 + 自动回车标记 + 折叠状态），可用于备份 / 迁移 / 再导入
 *   - 文本：人类可读，适合直接发给别人（微信、QQ 等）
 */
public class KeyBackup {

    /** 导出文件里的格式标识与版本，便于以后兼容 / 导入校验。 */
    public static final String FORMAT = "ckime-keys";
    public static final int FORMAT_VERSION = 1;

    private KeyBackup() {}

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    public static String toJson(Context ctx) {
        List<KeyGroup> groups = KeyStore.loadGroups(ctx);
        Set<String> collapsed = KeyStore.loadCollapsed(ctx);

        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT);
            root.put("version", FORMAT_VERSION);
            root.put("app", "自定义按键输入法");
            root.put("exportedAt", now("yyyy-MM-dd HH:mm:ss"));

            JSONArray ga = new JSONArray();
            for (KeyGroup g : groups) {
                JSONObject go = new JSONObject();
                go.put("name", g.name);
                go.put("collapsed", collapsed.contains(g.name));

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
            root.put("totalGroups", groups.size());
            root.put("totalKeys", countKeys(groups));
        } catch (JSONException ignored) {
        }
        String out;
        try {
            out = root.toString(2);
        } catch (Throwable t) {
            out = root.toString();
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 文本
    // ------------------------------------------------------------------

    public static String toText(Context ctx) {
        List<KeyGroup> groups = KeyStore.loadGroups(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("自定义按键导出（").append(now("yyyy-MM-dd HH:mm")).append("）\n");
        sb.append("共 ").append(groups.size()).append(" 组 / ")
                .append(countKeys(groups)).append(" 个按键\n");
        sb.append("──────────────────\n");
        for (KeyGroup g : groups) {
            sb.append("【").append(g.name).append("】(").append(g.keys.size()).append(")\n");
            for (CustomKey k : g.keys) {
                sb.append("  ").append(k.text)
                        .append(k.autoEnter ? "  [自动回车]" : "  [仅输入]")
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 导入（解析）
    // ------------------------------------------------------------------

    /** 解析结果：分组 + 折叠状态。 */
    public static class Backup {
        public final List<KeyGroup> groups = new ArrayList<>();
        public final Set<String> collapsed = new HashSet<>();

        public int keyCount() {
            int n = 0;
            for (KeyGroup g : groups) n += g.keys.size();
            return n;
        }
    }

    /**
     * 解析导出的 JSON（也兼容「SharedPreferences 里的原始格式」：只有 groups 没有 format）。
     * @throws JSONException 内容不是合法 JSON 或缺少 groups 字段
     */
    public static Backup parse(String json) throws JSONException {
        if (json == null) throw new JSONException("内容为空");
        String s = json.trim();
        if (s.isEmpty()) throw new JSONException("内容为空");
        // 容忍前后有说明文字：截取第一个 { 到最后一个 }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new JSONException("没有找到 JSON 内容");
        s = s.substring(a, b + 1);

        JSONObject root = new JSONObject(s);
        JSONArray ga = root.optJSONArray("groups");
        if (ga == null) throw new JSONException("缺少 groups 字段，可能不是本输入法的导出文件");

        Backup out = new Backup();
        for (int i = 0; i < ga.length(); i++) {
            JSONObject go = ga.optJSONObject(i);
            if (go == null) continue;
            String name = go.optString("name", KeyStore.DEFAULT_GROUP);
            if (name == null || name.trim().isEmpty()) name = KeyStore.DEFAULT_GROUP;
            name = name.trim();

            KeyGroup kg = new KeyGroup(name);
            if (go.optBoolean("collapsed", false)) out.collapsed.add(name);

            JSONArray ka = go.optJSONArray("keys");
            if (ka != null) {
                for (int j = 0; j < ka.length(); j++) {
                    JSONObject ko = ka.optJSONObject(j);
                    if (ko == null) continue;
                    String text = ko.optString("text", "");
                    if (text == null || text.isEmpty()) continue;
                    kg.keys.add(new CustomKey(text, ko.optBoolean("autoEnter", true), name));
                }
            }
            out.groups.add(kg);
        }
        if (out.groups.isEmpty()) throw new JSONException("文件里没有任何分组");
        return out;
    }

    // ------------------------------------------------------------------

    public static String suggestFileName() {
        return "ckime-keys-" + now("yyyyMMdd-HHmm") + ".json";
    }

    private static int countKeys(List<KeyGroup> groups) {
        int n = 0;
        for (KeyGroup g : groups) n += g.keys.size();
        return n;
    }

    private static String now(String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault())
                .format(new Date(System.currentTimeMillis()));
    }
}
