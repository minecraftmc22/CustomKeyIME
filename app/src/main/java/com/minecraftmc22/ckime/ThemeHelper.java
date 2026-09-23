package com.minecraftmc22.ckime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;

import java.io.InputStream;

/**
 * Material You 3 配色。
 *  - Android 12+ (API 31)：读取系统动态色（system_accent1 / system_neutral1）
 *  - 低版本：回退到 M3 深色基线（紫）
 * 提供键盘背景色合成（明暗 + 透明度）。
 */
public final class ThemeHelper {

    public static int accent;          // 强调色（用于标签、候选、自定义按键底）
    public static int accentContainer; // 功能键背景
    public static int surface;         // 键盘背景基色
    public static int surfaceVariant;  // 普通按键背景
    public static int surfaceVariant2; // 候选词背景
    public static int onSurface;       // 主文字
    public static int onSurfaceDim;    // 次要文字
    public static final int ON_ACCENT = 0xFF21005D;  // 强调色上的深色文字

    private static boolean inited = false;

    public static void init(Context c) {
        if (inited) return;
        inited = true;
        if (Build.VERSION.SDK_INT >= 31) {
            accent = color(c, android.R.color.system_accent1_200);
            accentContainer = color(c, android.R.color.system_accent1_500);
            surface = color(c, android.R.color.system_neutral1_900);
            surfaceVariant = color(c, android.R.color.system_neutral1_800);
            surfaceVariant2 = color(c, android.R.color.system_neutral1_700);
            onSurface = color(c, android.R.color.system_neutral1_50);
            onSurfaceDim = color(c, android.R.color.system_neutral1_300);
        } else {
            accent = 0xFFD0BCFF;
            accentContainer = 0xFF4F378B;
            surface = 0xFF141218;
            surfaceVariant = 0xFF49454F;
            surfaceVariant2 = 0xFF2B2930;
            onSurface = 0xFFE6E0E9;
            onSurfaceDim = 0xFF938F99;
        }
    }

    private static int color(Context c, int res) {
        if (Build.VERSION.SDK_INT >= 23) return c.getColor(res);
        return c.getResources().getColor(res);
    }

    /** 键盘背景色：基色 → 明暗调节 → 透明度 */
    public static int background(int alpha, int brightness) {
        int base = applyBrightness(surface, brightness);
        return withAlpha(base, alpha);
    }

    /**
     * 键盘背景 Drawable：
     *  - 设置了自定义背景图片 → 图片（透明度 + 明暗叠加层）
     *  - 未设置 → 主题纯色背景
     */
    public static Drawable keyboardBackground(Context c, int alpha, int brightness) {
        Drawable img = customImage(c);
        if (img == null) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(background(alpha, brightness));
            return g;
        }
        try {
            if (brightness != 0) {
                // 用半透明黑/白叠加模拟明暗（图片本身不好直接改明度）
                int k = Math.round(Math.abs(brightness) * 255f / 100f * 0.7f);
                int overlay = brightness > 0
                        ? withAlpha(Color.WHITE, k)
                        : withAlpha(Color.BLACK, k);
                LayerDrawable ld = new LayerDrawable(
                        new Drawable[]{img, new ColorDrawable(overlay)});
                ld.setAlpha(clampAlpha(alpha));
                return ld;
            }
            Drawable d = img.mutate();
            d.setAlpha(clampAlpha(alpha));
            return d;
        } catch (Throwable t) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(background(alpha, brightness));
            return g;
        }
    }

    /** 读取用户选择的背景图片；未设置或读取失败返回 null。 */
    public static Drawable customImage(Context c) {
        String uri = AppSettings.getBgImageUri(c);
        if (uri == null || uri.isEmpty()) return null;
        try {
            return customImage(c, Uri.parse(uri));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读取指定 Uri 的图片（用于设置页预览 / 校验）。失败返回 null。 */
    public static Drawable customImage(Context c, Uri uri) {
        if (uri == null) return null;
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            Drawable d = Drawable.createFromStream(in, "ckime-bg");
            if (d instanceof BitmapDrawable) {
                BitmapDrawable bd = (BitmapDrawable) d;
                bd.setGravity(Gravity.FILL);
                bd.setFilterBitmap(true);
                bd.setDither(true);
            }
            return d;
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }

    private static int clampAlpha(int a) {
        return a < 0 ? 0 : (a > 255 ? 255 : a);
    }

    /** 明暗调节（-100~+100），基于 HSV 明度 */
    public static int applyBrightness(int color, int delta) {
        if (delta == 0) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = clamp01(hsv[2] + delta / 100f);
        return Color.HSVToColor(hsv);
    }

    public static int withAlpha(int color, int alpha) {
        if (alpha < 0) alpha = 0;
        if (alpha > 255) alpha = 255;
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
