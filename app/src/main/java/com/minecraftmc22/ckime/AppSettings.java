package com.minecraftmc22.ckime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 键盘外观与行为设置（SharedPreferences）。
 *  - 键盘高度百分比（70~160）
 *  - 背景透明度（0~255，255 为不透明）
 *  - 背景明暗（-100~+100）
 */
public class AppSettings {

    public static final String PREFS = "ckime_settings";
    private static final String K_HEIGHT = "keyHeight";
    private static final String K_ALPHA = "bgAlpha";
    private static final String K_BRIGHT = "bgBrightness";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 键盘高度百分比，70~160，默认 100 */
    public static int getKeyHeightPercent(Context c) {
        return clamp(sp(c).getInt(K_HEIGHT, 100), 70, 160);
    }

    public static void setKeyHeightPercent(Context c, int v) {
        sp(c).edit().putInt(K_HEIGHT, clamp(v, 70, 160)).apply();
    }

    /** 背景透明度 0~255，默认 255（不透明） */
    public static int getBgAlpha(Context c) {
        return clamp(sp(c).getInt(K_ALPHA, 255), 0, 255);
    }

    public static void setBgAlpha(Context c, int v) {
        sp(c).edit().putInt(K_ALPHA, clamp(v, 0, 255)).apply();
    }

    /** 背景明暗 -100~+100，默认 0 */
    public static int getBgBrightness(Context c) {
        return clamp(sp(c).getInt(K_BRIGHT, 0), -100, 100);
    }

    public static void setBgBrightness(Context c, int v) {
        sp(c).edit().putInt(K_BRIGHT, clamp(v, -100, 100)).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
