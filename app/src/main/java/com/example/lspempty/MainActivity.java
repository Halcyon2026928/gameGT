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
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_THEME_LIGHT = "theme_light";

    private SwitchMaterial swStartup;
    private SwitchMaterial swApps;
    private SwitchMaterial swRecommend;
    private SharedPreferences prefs;

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
        LinearLayout cardStartup = findViewById(R.id.cardStartup);
        LinearLayout cardApps = findViewById(R.id.cardApps);
        LinearLayout cardRecommend = findViewById(R.id.cardRecommend);
        View btnRestart = findViewById(R.id.btnRestart);
        View btnTheme = findViewById(R.id.btnTheme);
        View btnRefresh = findViewById(R.id.btnRefresh);
        ImageView ivThemeIcon = findViewById(R.id.ivThemeIcon);
        View statusDot = findViewById(R.id.statusDot);
        TextView tvStatus = findViewById(R.id.tvStatus);
        View warnNotActivated = findViewById(R.id.warnNotActivated);

        // 主题按钮图标：深色主题→显示月亮；浅色主题→显示太阳
        ivThemeIcon.setImageResource(light ? R.drawable.ic_theme_sun : R.drawable.ic_theme_moon);

        // 读取当前状态（默认都开）
        swStartup.setChecked(prefs.getBoolean(SettingsProvider.KEY_BLOCK_STARTUP, true));
        swApps.setChecked(prefs.getBoolean(SettingsProvider.KEY_REMOVE_LIMIT, true));
        swRecommend.setChecked(prefs.getBoolean(SettingsProvider.KEY_HIDE_RECOMMEND, true));

        refreshActivation(statusDot, tvStatus, cardStartup, cardApps, cardRecommend, warnNotActivated);

        // 点击卡片也能切换
        cardStartup.setOnClickListener(v -> swStartup.toggle());
        cardApps.setOnClickListener(v -> swApps.toggle());
        cardRecommend.setOnClickListener(v -> swRecommend.toggle());

        swStartup.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_BLOCK_STARTUP, isChecked).apply());

        swApps.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_REMOVE_LIMIT, isChecked).apply());

        swRecommend.setOnCheckedChangeListener((buttonView, isChecked) ->
                prefs.edit().putBoolean(SettingsProvider.KEY_HIDE_RECOMMEND, isChecked).apply());

        // 重启游戏助手（force-stop，下次进入时重新加载 hook 和配置）
        btnRestart.setOnClickListener(v -> restartGameAssistant());

        // 手动重新检测激活状态
        btnRefresh.setOnClickListener(v -> {
            checkActivationWithRoot(null);
        });

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
        // 每次回到界面都重新检测激活状态
        if (swStartup != null) {
            // 先按本地标记显示，再用 root 确认
            prefs = getSharedPreferences(SettingsProvider.PREFS_NAME, MODE_PRIVATE);
            refreshActivation(findViewById(R.id.statusDot), findViewById(R.id.tvStatus),
                    findViewById(R.id.cardStartup), findViewById(R.id.cardApps),
                    findViewById(R.id.cardRecommend), findViewById(R.id.warnNotActivated));
            checkActivationWithRoot(null);
        }
    }

    private void refreshActivation(View statusDot, TextView tvStatus,
                                   LinearLayout cardStartup, LinearLayout cardApps,
                                   LinearLayout cardRecommend, View warnNotActivated) {
        // 激活状态：hook 端在游戏助手进程写入 activated=true 才视为已激活
        boolean activated = prefs.getBoolean(SettingsProvider.KEY_ACTIVATED, false);
        if (activated) {
            statusDot.setBackgroundResource(R.drawable.status_dot_on);
            tvStatus.setText("已激活");
            tvStatus.setTextColor(getColor(R.color.accent_cyan));
            setSwitchesEnabled(true);
            cardStartup.setEnabled(true);
            cardApps.setEnabled(true);
            cardRecommend.setEnabled(true);
            warnNotActivated.setVisibility(View.GONE);
        } else {
            statusDot.setBackgroundResource(R.drawable.status_dot_off);
            tvStatus.setText("未激活");
            tvStatus.setTextColor(getColor(R.color.status_off));
            // 未激活：开关置灰禁用、卡片不可点
            setSwitchesEnabled(false);
            cardStartup.setEnabled(false);
            cardApps.setEnabled(false);
            cardRecommend.setEnabled(false);
            warnNotActivated.setVisibility(View.VISIBLE);
        }
    }

    private void setSwitchesEnabled(boolean enabled) {
        swStartup.setEnabled(enabled);
        swApps.setEnabled(enabled);
        swRecommend.setEnabled(enabled);
    }

    /**
     * 用 root 直接检测游戏助手进程是否加载了本模块（LSPosed 作用域生效的可靠证据）。
     * 检测成功即写 activated=true 到本地 prefs 并刷新 UI。
     */
    private void checkActivationWithRoot(Runnable onDone) {
        new Thread(() -> {
            boolean found = false;
            try {
                // 遍历 com.oplus.games 所有进程，查找 maps 中是否含模块 dex 路径
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "for p in $(pidof com.oplus.games); do grep -l 'lspempty\\|NoSplash' /proc/$p/maps 2>/dev/null; done"});
                p.waitFor();
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("lspempty") || line.contains("NoSplash")) {
                        found = true;
                        break;
                    }
                }
                br.close();
            } catch (Exception e) {
                found = false;
            }
            final boolean ok = found;
            runOnUiThread(() -> {
                if (ok) {
                    // 确认生效，落盘标记
                    prefs = getSharedPreferences(SettingsProvider.PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putBoolean(SettingsProvider.KEY_ACTIVATED, true).commit();
                }
                // 刷新 UI
                refreshActivation(findViewById(R.id.statusDot), findViewById(R.id.tvStatus),
                        findViewById(R.id.cardStartup), findViewById(R.id.cardApps),
                        findViewById(R.id.cardRecommend), findViewById(R.id.warnNotActivated));
                if (onDone != null) onDone.run();
            });
        }).start();
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
