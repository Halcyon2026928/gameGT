package com.example.lspempty;

import android.graphics.drawable.Drawable;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 模块：屏蔽「游戏助手」com.oplus.games 的启动动画 + 解除助手栏应用数量限制。
 *
 * 反编译分析结论（10.41.3，包名 com.oplus.games）：
 *
 * 【启动动画】
 * 1. 启动动画的唯一入口漏斗是
 *    com.content.games.arch.newarch.capabilities.StartupAnimationFeature
 *    的方法 c(String packageName, Drawable drawable)（createStartupAnimation）。
 *    该方法内部按条件分派到三条路径：
 *      - GTBoost 动画   : business.module.gtboost.GTBoostViewManager.h()
 *      - HyperBoost 动画: business.secondarypanel.manager.c.h(pkg, "float", drawable)
 *      - unique 动画    : IFeatureStartUpUnique.showStartUpAnim(...)
 * 2. GTBoost 浮层由 GTBoostView 渲染（全屏 overlay，onCreate 中播放帧动画并 2750ms 后结束）。
 *    把 StartupAnimationFeature.c(String, Drawable) 替换为空实现，即可一次屏蔽所有启动动画；
 *    同时对 GTBoostViewManager.h() 再做一层防御性屏蔽。
 *
 * 【助手栏应用数量限制】
 * 1. 最大数量由 AppDataProviderCommon.getMaxAppCountLimit() 提供（默认 20），
 *    被 DesignatedAppsHelper 静态初始化、AppDataSideBarEditModel、AppDataProviderBase 等多处消费。
 * 2. 添加应用时的直接守门是 DesignatedAppsHelper.u(boolean isAuto)：
 *    若当前数量 >= max，则返回 true（达到上限）并弹「已添加 N 个应用，无法继续添加」toast，拒绝添加。
 *    解除方式：① getMaxAppCountLimit() 返回大值（影响 UI 可添加数量计算与保存列表截断）；
 *              ② u(boolean) 始终返回 false（直接绕过添加守门，防御静态字段已初始化的情况）。
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
        hookGTBoost(cl);

        // 3) 解除助手栏应用数量限制（默认 20 个）
        hookAppCountLimit(cl);
    }

    private void hookStartupAnimationFunnel(ClassLoader cl) {
        try {
            Class<?> feature = XposedHelpers.findClass(
                    "com.content.games.arch.newarch.capabilities.StartupAnimationFeature", cl);
            XposedHelpers.findAndHookMethod(feature, "c", String.class, Drawable.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
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
                    return null;
                }
            });
            XposedBridge.log(TAG + " hooked GTBoostViewManager.h()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook GTBoostViewManager.h failed: " + t);
        }
    }

    private void hookAppCountLimit(ClassLoader cl) {
        // ① 把最大应用数量从 20 提到 1000，影响所有消费方（UI 可添加数量、保存列表截断等）
        try {
            Class<?> provider = XposedHelpers.findClass(
                    "business.gamedock.sort.AppDataProviderCommon", cl);
            XposedHelpers.findAndHookMethod(provider, "getMaxAppCountLimit",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return 1000;
                        }
                    });
            XposedBridge.log(TAG + " hooked AppDataProviderCommon.getMaxAppCountLimit -> 1000");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook getMaxAppCountLimit failed: " + t);
        }

        // ② 直接绕过添加守门：DesignatedAppsHelper.u(boolean) 始终返回 false（未达上限）
        try {
            Class<?> helper = XposedHelpers.findClass(
                    "business.module.customdefine.apps.DesignatedAppsHelper", cl);
            XposedHelpers.findAndHookMethod(helper, "u", boolean.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return false;
                        }
                    });
            XposedBridge.log(TAG + " hooked DesignatedAppsHelper.u(boolean) -> false");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook DesignatedAppsHelper.u failed: " + t);
        }
    }
}
