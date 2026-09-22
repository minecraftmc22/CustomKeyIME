package com.minecraftmc22.CKIME;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义按键的持久化存储：SharedPreferences + JSON。
 * 输入法服务和设置界面共用同一份数据，保存后键盘实时刷新。
 */
public class KeyStore {

    public static final String PREFS = "custom_keys_prefs";
    private static final String JSON = "keys_json";

    public static List<CustomKey> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<CustomKey> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(JSON, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new CustomKey(o.optString("text"), o.optBoolean("autoEnter", true)));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    public static void save(Context ctx, List<CustomKey> list) {
        JSONArray arr = new JSONArray();
        for (CustomKey k : list) {
            JSONObject o = new JSONObject();
            try {
                o.put("text", k.text);
                o.put("autoEnter", k.autoEnter);
                arr.put(o);
            } catch (JSONException ignored) {
            }
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(JSON, arr.toString())
                .apply();
    }
}
