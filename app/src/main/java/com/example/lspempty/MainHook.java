package com.example.lspempty;

import android.graphics.drawable.Drawable;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 模块：屏蔽「游戏助手」com.oplus.games 的启动动画。
 *
 * 反编译分析结论（10.41.3，包名 com.oplus.games）：
 * 1. 启动动画的唯一入口漏斗是
 *    com.content.games.arch.newarch.capabilities.StartupAnimationFeature
 *    的私有方法 c(String packageName, Drawable drawable)（createStartupAnimation）。
 *    该方法内部按条件分派到三条路径：
 *      - GTBoost 动画   : business.module.gtboost.GTBoostViewManager.h()
 *      - HyperBoost 动画: business.secondarypanel.manager.c.h(pkg, "float", drawable)
 *      - unique 动画    : IFeatureStartUpUnique.showStartUpAnim(...)
 * 2. GTBoost 浮层由 GTBoostView 渲染（全屏 overlay，onCreate 中播放帧动画并 2750ms 后结束）。
 *
 * 因此：把 StartupAnimationFeature.c(String, Drawable) 替换为空实现，即可一次屏蔽所有启动动画；
 * 同时对 GTBoostViewManager.h() 再做一层防御性屏蔽（直接阻止 GTBoost 浮层创建）。
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "NoSplash";
    private static final String TARGET_PKG = "com.oplus.games";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) {
            return;
        }
        ClassLoader cl = lpparam.classLoader;

        // 1) 唯一入口漏斗：屏蔽所有启动动画（GTBoost / HyperBoost / unique）
        hookStartupAnimationFunnel(cl);

        // 2) 防御性：直接屏蔽 GTBoost 浮层创建入口
//        hookGTBoost(cl);
    }

    private void hookStartupAnimationFunnel(ClassLoader cl) {
        try {
            Class<?> feature = XposedHelpers.findClass(
                    "com.content.games.arch.newarch.capabilities.StartupAnimationFeature", cl);
            XposedHelpers.findAndHookMethod(feature, "c", String.class, Drawable.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            // 什么都不做：不创建任何启动动画
                            return null;
                        }
                    });
            XposedBridge.log(TAG + " hooked StartupAnimationFeature.c(String, Drawable)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook StartupAnimationFeature.c failed: " + t);
        }
    }

    private void hookGTBoost(ClassLoader cl) {
        try {
            Class<?> mgr = XposedHelpers.findClass("business.module.gtboost.GTBoostViewManager", cl);
            XposedHelpers.findAndHookMethod(mgr, "h", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    // 什么都不做：不创建 GTBoost 浮层
                    return null;
                }
            });
            XposedBridge.log(TAG + " hooked GTBoostViewManager.h()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook GTBoostViewManager.h failed: " + t);
        }
    }
}
