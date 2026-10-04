package com.example.lspempty;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_THEME_LIGHT = "theme_light";

    private SwitchMaterial swStartup;
    private SwitchMaterial swApps;
    private SwitchMaterial swRecommend;
    private LinearLayout cardStartup;
    private LinearLayout cardApps;
    private LinearLayout cardRecommend;
    private View statusDot;
    private TextView tvStatus;
    private TextView tvHint;
    private SharedPreferences prefs;
    private boolean moduleActivated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 主题切换必须在 setContentView 之前设置
        prefs = getSharedPreferences(SettingsProvider.PREFS_NAME, MODE_PRIVATE);
        boolean light = prefs.getBoolean(KEY_THEME_LIGHT, false);
        AppCompatDelegate.setDefaultNightMode(light
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swStartup = findViewById(R.id.swStartup);
        swApps = findViewById(R.id.swApps);
        swRecommend = findViewById(R.id.swRecommend);
        cardStartup = findViewById(R.id.cardStartup);
        cardApps = findViewById(R.id.cardApps);
        cardRecommend = findViewById(R.id.cardRecommend);
        View btnRestart = findViewById(R.id.btnRestart);
        View btnTheme = findViewById(R.id.btnTheme);
        ImageView ivThemeIcon = findViewById(R.id.ivThemeIcon);
        statusDot = findViewById(R.id.statusDot);
        tvStatus = findViewById(R.id.tvStatus);
        tvHint = findViewById(R.id.tvHint);

        // 主题按钮图标：深色主题→显示月亮；浅色主题→显示太阳
        ivThemeIcon.setImageResource(light ? R.drawable.ic_theme_sun : R.drawable.ic_theme_moon);

        applyActivationUi(false);
        refreshActivation();

        // 读取当前状态（默认都开）
        swStartup.setChecked(prefs.getBoolean(SettingsProvider.KEY_BLOCK_STARTUP, true));
        swApps.setChecked(prefs.getBoolean(SettingsProvider.KEY_REMOVE_LIMIT, true));
        swRecommend.setChecked(prefs.getBoolean(SettingsProvider.KEY_HIDE_RECOMMEND, true));

        // 点击卡片也能切换（未激活时卡片已禁用）
        cardStartup.setOnClickListener(v -> {
            if (moduleActivated) swStartup.toggle();
        });
        cardApps.setOnClickListener(v -> {
            if (moduleActivated) swApps.toggle();
        });
        cardRecommend.setOnClickListener(v -> {
            if (moduleActivated) swRecommend.toggle();
        });

        swStartup.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_BLOCK_STARTUP, isChecked).apply());

        swApps.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_REMOVE_LIMIT, isChecked).apply());

        swRecommend.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_HIDE_RECOMMEND, isChecked).apply());

        // 重启游戏助手（force-stop，下次进入时重新加载 hook 和配置）
        btnRestart.setOnClickListener(v -> restartGameAssistant());

        // 切换深浅主题
        btnTheme.setOnClickListener(v -> {
            boolean newLight = !prefs.getBoolean(KEY_THEME_LIGHT, false);
            prefs.edit().putBoolean(KEY_THEME_LIGHT, newLight).apply();
            AppCompatDelegate.setDefaultNightMode(newLight
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
            recreate();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshActivation();
    }

    /** 对照 LSPosed 作用域：未勾选 com.oplus.games 则为未激活。 */
    private void refreshActivation() {
        File cacheDir = getCacheDir();
        new Thread(() -> {
            boolean active = ActivationChecker.isGamesScopeEnabled(cacheDir);
            runOnUiThread(() -> applyActivationUi(active));
        }).start();
    }

    private void applyActivationUi(boolean active) {
        moduleActivated = active;
        statusDot.setBackgroundResource(active
                ? R.drawable.status_dot_on
                : R.drawable.status_dot_off);
        tvStatus.setText(active ? "已激活" : "未激活");
        tvStatus.setTextColor(getColor(active ? R.color.accent_cyan : R.color.status_off));

        float alpha = active ? 1f : 0.4f;
        setFeatureEnabled(cardStartup, swStartup, alpha, active);
        setFeatureEnabled(cardApps, swApps, alpha, active);
        setFeatureEnabled(cardRecommend, swRecommend, alpha, active);

        if (tvHint != null) {
            tvHint.setText(active
                    ? "修改开关后，需强制停止「游戏助手」再重新进入游戏才能生效。"
                    : "请在 LSPosed 中启用本模块，并勾选作用域「游戏助手」(com.oplus.games)。未勾选时功能不会生效。");
        }
    }

    private void setFeatureEnabled(View card, SwitchMaterial sw, float alpha, boolean enabled) {
        card.setAlpha(alpha);
        card.setEnabled(enabled);
        card.setClickable(enabled);
        sw.setEnabled(enabled);
        sw.setClickable(enabled);
        sw.setFocusable(enabled);
    }

    private void restartGameAssistant() {
        new Thread(() -> {
            boolean success = false;
            String error = null;
            try {
                Process su = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "am force-stop com.oplus.games"});
                su.waitFor();
                // 读 stderr 看有没有错误
                BufferedReader err = new BufferedReader(new InputStreamReader(su.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = err.readLine()) != null) sb.append(line);
                err.close();
                success = su.exitValue() == 0;
                if (!success) error = sb.toString();
            } catch (Exception e) {
                error = e.getMessage();
            }
            final boolean ok = success;
            final String errMsg = error;
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, "游戏助手已重启，下次进入生效", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "需要 Root 权限才能重启：" + errMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
}
