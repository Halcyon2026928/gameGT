# GTBoost — 游戏助手增强 LSPosed 模块

针对 OPPO/OnePlus「游戏助手」(`com.oplus.games`) 的 LSPosed 增强模块，提供以下功能：

- **屏蔽启动动画**：跳过游戏启动时的全屏 GTBoost 动画
- **解除应用数量限制**：助手栏可添加超过 20 个应用（上限 100）
- **隐藏推荐应用**：编辑应用时不显示「未安装应用」广告推荐

模块自带桌面设置入口（GTBoost），支持三个功能独立开关、深浅双主题切换、一键重启游戏助手。

## 原理

### 启动动画

启动动画的唯一入口漏斗是：

```
com.content.games.arch.newarch.capabilities.StartupAnimationFeature.c(String, Drawable)
```

该方法按特性开关分派到 GTBoost / HyperBoost / unique 三条路径。模块把 `StartupAnimationFeature.c(String, Drawable)` 替换为空实现，一次屏蔽全部三种；同时对 `GTBoostViewManager.h()` 做防御性空实现。

### 应用数量限制

核心常量 `AppDataProviderCommon.maxAppCountLimit = 20`（混淆后字段 `f`），通过反射获取真实 getter 方法对象并 hook，将返回值 20 改为 100。同时 hook `DesignatedAppsHelper.u(boolean)` 绕过添加守门。

### 隐藏推荐应用

- hook `CustomAppSearcher.c(...)`：搜索时的「未安装应用」分区替换为空列表
- hook `CusDefAppsModel.e(...)`：主网格混入的推荐应用（带下载角标），强制跳过注入

## Hook 点

| 功能 | Hook 点 |
|---|---|
| 启动动画 | `StartupAnimationFeature.c(String, Drawable)` → no-op |
| 启动动画防御 | `GTBoostViewManager.h()` → no-op |
| 应用数量 | `AppDataProviderCommon` 无参 int getter（反射获取）→ 返回 100 |
| 添加守门 | `DesignatedAppsHelper.u(boolean)` → setResult(false) |
| 搜索推荐 | `CustomAppSearcher.c(List, List, Set, List)` → 清空未安装分区 |
| 主网格推荐 | `CusDefAppsModel.e(List, List, boolean)` → 强制 skipInjectingUninstalled=true |

只在包名 `com.oplus.games` 时生效，均有 try/catch，找不到类不崩溃。

## 构建

```
gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 本项目 `gradle-wrapper.properties` 指向本地 `file:///D:/Androidstudio/gameGT/.build/gradle-8.7-bin.zip`（因网络 SSL 代理导致在线下载失败）。如需在线下载，改回 `https://services.gradle.org/distributions/gradle-8.7-bin.zip`。
> `gradle.properties` 已加 `-Djavax.net.ssl.trustStoreType=Windows-ROOT` 以适配 SSL 代理环境。

## 使用

1. 安装模块 APK
2. LSPosed 管理器 → 模块 → 启用 **GTBoost**
3. 作用域勾选 `com.oplus.games`（模块已通过 `xposedscope` 元数据预填）
4. 打开 GTBoost App，按需开关功能
5. 点「重启游戏助手」或强制停止游戏助手，重新进入游戏生效

## 项目结构

```
app/
  src/main/java/.../MainHook.java        Hook 实现
  src/main/java/.../MainActivity.java    桌面设置入口
  src/main/java/.../SettingsProvider.java 跨进程配置提供器
  src/main/AndroidManifest.xml            xposed 声明 + xposedscope
  src/main/assets/xposed_init             入口类
  src/main/res/                           布局、主题、图标
```

## 免责声明

本项目完全免费，无需任何付费即可获取使用。

禁止任何形式的二次开发收费行为，包括但不限于：转售、付费订阅、植入广告获取收益等商业牟利手段。

本项目仅供学习与研究用途，请勿用于违反设备使用协议或当地法律法规的场景。
