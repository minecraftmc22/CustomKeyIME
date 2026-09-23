package com.minecraftmc22.ckime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 键盘设置：高度 / 背景透明度 / 背景明暗 / 自定义背景图片。
 * 修改即时保存，键盘下一次弹出（或立即）生效。
 */
public class SettingsActivity extends Activity {

    private static final int REQ_PICK_BG = 2001;

    private View bgPreview;
    private TextView tvBgState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 键盘高度 70~160%
        final SeekBar sbHeight = findViewById(R.id.sbHeight);
        final TextView tvHeight = findViewById(R.id.tvHeight);
        sbHeight.setMax(160 - 70);
        sbHeight.setProgress(AppSettings.getKeyHeightPercent(this) - 70);
        tvHeight.setText(AppSettings.getKeyHeightPercent(this) + "%");
        sbHeight.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int v = 70 + p;
                tvHeight.setText(v + "%");
                AppSettings.setKeyHeightPercent(SettingsActivity.this, v);
            }
        });

        // 背景透明度 0~100%（映射 0~255）
        final SeekBar sbAlpha = findViewById(R.id.sbAlpha);
        final TextView tvAlpha = findViewById(R.id.tvAlpha);
        sbAlpha.setMax(100);
        sbAlpha.setProgress(Math.round(AppSettings.getBgAlpha(this) * 100f / 255f));
        tvAlpha.setText(Math.round(AppSettings.getBgAlpha(this) * 100f / 255f) + "%");
        sbAlpha.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                tvAlpha.setText(p + "%");
                AppSettings.setBgAlpha(SettingsActivity.this, Math.round(p * 255f / 100f));
            }
        });

        // 背景明暗 -100~+100
        final SeekBar sbBright = findViewById(R.id.sbBright);
        final TextView tvBright = findViewById(R.id.tvBright);
        sbBright.setMax(200);
        sbBright.setProgress(AppSettings.getBgBrightness(this) + 100);
        tvBright.setText(AppSettings.getBgBrightness(this) + "");
        sbBright.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                int v = p - 100;
                tvBright.setText(v + "");
                AppSettings.setBgBrightness(SettingsActivity.this, v);
            }
        });

        // 自定义背景图片
        bgPreview = findViewById(R.id.bgPreview);
        tvBgState = findViewById(R.id.tvBgState);
        findViewById(R.id.btnPickBg).setOnClickListener(v -> pickBackgroundImage());
        findViewById(R.id.btnClearBg).setOnClickListener(v -> {
            if (!AppSettings.hasBgImage(this)) {
                Toast.makeText(this, "当前没有自定义背景图片", Toast.LENGTH_SHORT).show();
                return;
            }
            AppSettings.setBgImageUri(this, "");
            updateBgPreview();
            Toast.makeText(this, "已移除背景图片", Toast.LENGTH_SHORT).show();
        });
        bgPreview.setOnLongClickListener(v -> {
            pickBackgroundImage();
            return true;
        });
        updateBgPreview();

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            AppSettings.setKeyHeightPercent(this, 100);
            AppSettings.setBgAlpha(this, 255);
            AppSettings.setBgBrightness(this, 0);
            AppSettings.setBgImageUri(this, "");
            sbHeight.setProgress(30);
            tvHeight.setText("100%");
            sbAlpha.setProgress(100);
            tvAlpha.setText("100%");
            sbBright.setProgress(100);
            tvBright.setText("0");
            updateBgPreview();
            Toast.makeText(this, "已恢复默认（含移除背景图片）", Toast.LENGTH_SHORT).show();
        });
    }

    // ------------------------------------------------------------------
    // 背景图片
    // ------------------------------------------------------------------

    private void pickBackgroundImage() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        try {
            startActivityForResult(it, REQ_PICK_BG);
        } catch (Throwable t) {
            Toast.makeText(this, "当前系统没有可用的图片选择器", Toast.LENGTH_LONG).show();
        }
    }

    /** 预览：用与键盘相同的合成方式渲染，所见即所得。 */
    private void updateBgPreview() {
        if (bgPreview == null) return;
        Drawable d = ThemeHelper.keyboardBackground(this,
                AppSettings.getBgAlpha(this), AppSettings.getBgBrightness(this));
        bgPreview.setBackground(d);
        tvBgState.setText(AppSettings.hasBgImage(this)
                ? "当前：自定义图片背景"
                : getString(R.string.settings_bg_none));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_BG) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        // 申请长期读取权限，重启后依然可用
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Throwable ignored) {
        }
        if (ThemeHelper.customImage(this, uri) == null) {
            // 先写再验：若读不出来就不保存
            Toast.makeText(this, "无法读取该图片，请换一张试试", Toast.LENGTH_LONG).show();
            return;
        }
        AppSettings.setBgImageUri(this, uri.toString());
        updateBgPreview();
        Toast.makeText(this, "背景图片已设置，键盘立即生效", Toast.LENGTH_SHORT).show();
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
