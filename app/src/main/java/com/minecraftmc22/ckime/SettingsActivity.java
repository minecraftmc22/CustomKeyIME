package com.minecraftmc22.ckime;

import android.app.Activity;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 键盘设置：高度 / 背景透明度 / 背景明暗。
 * 修改即时保存，键盘下一次弹出即生效。
 */
public class SettingsActivity extends Activity {

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

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            AppSettings.setKeyHeightPercent(this, 100);
            AppSettings.setBgAlpha(this, 255);
            AppSettings.setBgBrightness(this, 0);
            sbHeight.setProgress(30);
            tvHeight.setText("100%");
            sbAlpha.setProgress(100);
            tvAlpha.setText("100%");
            sbBright.setProgress(100);
            tvBright.setText("0");
        });
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
