package com.example.lspempty;

import android.app.Application;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 模块：屏蔽「游戏助手」com.oplus.games 的启动动画 + 解除助手栏应用数量限制。
 * 功能均可通过桌面 App「游戏助手增强」独立开关，开关实时生效（无需重启目标应用）。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "NoSplash";
    private static final String TARGET_PKG = "com.oplus.games";

    // 目标进程的 Application Context（hook Application.onCreate 捕获，比反射更可靠）
    private static volatile Context sAppContext = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        ClassLoader cl = lpparam.classLoader;

        XposedBridge.log(TAG + " module loaded for " + TARGET_PKG);

        // 0) 立即写激活标记（只要 hook 被加载就写，不依赖 Application.onCreate）
        markActivated(lpparam);

        // 1) 捕获 Application Context，用于跨进程读配置
        hookApplicationContext();

        // 2) 启动动画漏斗 + GTBoost 防御
        hookStartupAnimation(cl);

        // 3) 解除助手栏应用数量限制
        hookAppCountLimit(cl);

        // 4) 隐藏「未安装应用」推荐广告区
        hookHideRecommended(cl);
    }

    private void hookApplicationContext() {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sAppContext = (Application) param.thisObject;
                            XposedBridge.log(TAG + " Application context captured");
                            // 再次确认写激活标记（兜底）
                            markActivated(null);
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook Application.onCreate failed: " + t);
        }
    }

    /** 写入「已激活」标记（跨进程写回模块 SettingsProvider）。lpparam 可为 null。 */
    private static void markActivated(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Context ctx = sAppContext;
            if (ctx == null) {
                // handleLoadPackage 阶段：通过反射 ActivityThread.currentApplication() 获取
                try {
                    Class<?> at = Class.forName("android.app.ActivityThread");
                    Object app = at.getMethod("currentApplication").invoke(null);
                    if (app != null) ctx = (Context) app;
                } catch (Throwable ignored) {}
            }
            if (ctx == null && lpparam != null) {
                // 最后兜底：用 AndroidAppHelper（LSPosed 内部类）
                try {
                    Class<?> aah = Class.forName("de.robv.android.xposed.AndroidAppHelper");
                    Object app = aah.getMethod("currentApplication").invoke(null);
                    if (app != null) ctx = (Context) app;
                } catch (Throwable ignored) {}
            }
            if (ctx == null) {
                XposedBridge.log(TAG + " markActivated: context still null, skip");
                return;
            }
            Bundle b = new Bundle();
            b.putBoolean(SettingsProvider.EXTRA_VALUE, true);
            Bundle result = ctx.getContentResolver().call(
                    SettingsProvider.CONTENT_URI,
                    SettingsProvider.METHOD_SET_BOOLEAN,
                    SettingsProvider.KEY_ACTIVATED, b);
            XposedBridge.log(TAG + " markActivated: written activated=true, result=" + result);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " markActivated failed: " + t);
        }
    }

    // ---------- 配置读取（每次实时读，不缓存，保证开关即时生效） ----------

    private static boolean isBlockStartup() {
        return readPref(SettingsProvider.KEY_BLOCK_STARTUP, true);
    }

    private static boolean isRemoveLimit() {
        return readPref(SettingsProvider.KEY_REMOVE_LIMIT, true);
    }

    private static boolean isHideRecommend() {
        return readPref(SettingsProvider.KEY_HIDE_RECOMMEND, true);
    }

    private static boolean readPref(String key, boolean def) {
        try {
            Context ctx = sAppContext != null ? sAppContext : getCurrentApplication();
            if (ctx == null) return def;
            Bundle b = ctx.getContentResolver().call(
                    SettingsProvider.CONTENT_URI,
                    SettingsProvider.METHOD_GET_BOOLEAN, key, null);
            if (b != null) {
                return b.getBoolean(SettingsProvider.EXTRA_VALUE, def);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " readPref " + key + " failed: " + t);
        }
        return def;
    }

    /** 反射调用 AndroidAppHelper.currentApplication()（兜底，该类不在编译用的 API jar 中）。 */
    private static Context getCurrentApplication() {
        try {
            Class<?> helper = Class.forName("de.robv.android.xposed.AndroidAppHelper");
            java.lang.reflect.Method m = helper.getMethod("currentApplication");
            return (Context) m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------- 启动动画 ----------

    private void hookStartupAnimation(ClassLoader cl) {
        // 漏斗入口：StartupAnimationFeature.c(String, Drawable)
        try {
            Class<?> feature = XposedHelpers.findClass(
                    "com.content.games.arch.newarch.capabilities.StartupAnimationFeature", cl);
            XposedBridge.hookAllMethods(feature, "c", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isBlockStartup()) {
                        param.setResult(null);
                    }
                }
            });
            XposedBridge.log(TAG + " hooked StartupAnimationFeature.c (conditional)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook StartupAnimationFeature.c failed: " + t);
        }

        // 防御性：GTBoostViewManager.h()
        try {
            Class<?> mgr = XposedHelpers.findClass("business.module.gtboost.GTBoostViewManager", cl);
            XposedBridge.hookAllMethods(mgr, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isBlockStartup()) {
                        param.setResult(null);
                    }
                }
            });
            XposedBridge.log(TAG + " hooked GTBoostViewManager.h (conditional)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook GTBoostViewManager.h failed: " + t);
        }
    }

    // ---------- 应用数量限制 ----------

    private void hookAppCountLimit(ClassLoader cl) {
        // ① getDeclaredMethods() 拿真实 Method 对象，afterHookedMethod 里把 20 改 100
        try {
            Class<?> commonClass = XposedHelpers.findClass(
                    "business.gamedock.sort.AppDataProviderCommon", cl);
            int hooked = 0;
            for (java.lang.reflect.Method m : commonClass.getDeclaredMethods()) {
                if (m.getParameterTypes().length != 0) continue;
                if (m.getReturnType() != int.class) continue;
                final String methodName = m.getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isRemoveLimit()) return;
                        Object res = param.getResult();
                        if (res instanceof Integer && (Integer) res == 20) {
                            param.setResult(100);
                        }
                    }
                });
                XposedBridge.log(TAG + " hooked int-getter " + commonClass.getSimpleName()
                        + "." + methodName + "() (conditional 20->100)");
                hooked++;
            }
            if (hooked == 0) {
                XposedBridge.log(TAG + " WARNING: no no-arg int method in AppDataProviderCommon");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook int-getters failed: " + t);
        }

        // ② DesignatedAppsHelper.u(boolean) — 开启时直接返回 false（未达上限）
        try {
            Class<?> helper = XposedHelpers.findClass(
                    "business.module.customdefine.apps.DesignatedAppsHelper", cl);
            XposedHelpers.findAndHookMethod(helper, "u", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isRemoveLimit()) {
                        param.setResult(false);
                    }
                }
            });
            XposedBridge.log(TAG + " hooked DesignatedAppsHelper.u(boolean) (conditional)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook DesignatedAppsHelper.u failed: " + t);
        }
    }

    // ---------- 隐藏推荐应用 ----------

    private void hookHideRecommended(ClassLoader cl) {
        // ① CustomAppSearcher.c() — 搜索时的「未安装应用」分区，把 notInstalled 列表清空
        try {
            Class<?> searcher = XposedHelpers.findClass(
                    "business.module.customdefine.apps.search.CustomAppSearcher", cl);
            XposedHelpers.findAndHookMethod(searcher, "c",
                    java.util.List.class, java.util.List.class,
                    java.util.Set.class, java.util.List.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isHideRecommend()) return;
                            Object merged = param.getResult();
                            if (merged == null) return;
                            try {
                                java.util.List<?> installed =
                                        (java.util.List<?>) XposedHelpers.callMethod(merged, "a");
                                Object newMerged = merged.getClass()
                                        .getConstructor(java.util.List.class, java.util.List.class)
                                        .newInstance(installed, java.util.Collections.emptyList());
                                param.setResult(newMerged);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " hideRecommended filter failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + " hooked CustomAppSearcher.c (hide recommended)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook CustomAppSearcher.c failed: " + t);
        }

        // ② CusDefAppsModel.e(List, List, boolean skipInjectingUninstalled)
        //    编辑应用主网格的推荐应用注入点，强制 skipInjectingUninstalled=true
        try {
            Class<?> model = XposedHelpers.findClass(
                    "business.module.customdefine.apps.vm.CusDefAppsModel", cl);
            XposedHelpers.findAndHookMethod(model, "e",
                    java.util.List.class, java.util.List.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (isHideRecommend()) {
                                param.args[2] = true;
                            }
                        }
                    });
            XposedBridge.log(TAG + " hooked CusDefAppsModel.e (skip inject uninstalled)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook CusDefAppsModel.e failed: " + t);
        }
    }
}
