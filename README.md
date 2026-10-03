# gameGT — 游戏助手去启动动画 LSPosed 模块

屏蔽 OPPO/OnePlus「游戏助手」(`com.oplus.games`, v10.41.3) 启动游戏时的全屏启动动画。

## 原理

反编译分析确认，启动动画的唯一入口漏斗是：

```
com.content.games.arch.newarch.capabilities.StartupAnimationFeature.c(String, Drawable)
```

该方法按特性开关分派到三条动画路径：

| 路径 | 实现类 |
|---|---|
| GTBoost | `business.module.gtboost.GTBoostViewManager` + `GTBoostView`（全屏浮层，2750ms） |
| HyperBoost | `business.secondarypanel.manager.c.h(pkg, "float", drawable)` |
| unique | `IFeatureStartUpUnique.showStartUpAnim(...)` |

模块把 `StartupAnimationFeature.c(String, Drawable)` 替换为空实现，一次屏蔽全部三种；同时对 `GTBoostViewManager.h()` 做防御性空实现。

## Hook 点

- `StartupAnimationFeature.c(java.lang.String, android.graphics.drawable.Drawable)` → no-op
- `GTBoostViewManager.h()` → no-op

只在包名 `com.oplus.games` 时生效，均有 try/catch，找不到类不崩溃。

## 构建

### 方式一：Gradle（Android Studio / 命令行）

```
gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 本项目 `gradle-wrapper.properties` 指向本地 `file:///D:/Androidstudio/gameGT/.build/gradle-8.7-bin.zip`（因网络 SSL 代理导致在线下载失败）。如需在线下载，改回 `https://services.gradle.org/distributions/gradle-8.7-bin.zip`。
> `gradle.properties` 已加 `-Djavax.net.ssl.trustStoreType=Windows-ROOT` 以适配 SSL 代理环境。

### 方式二：直接用 Android SDK 工具链（无需 Gradle）

```
powershell -ExecutionPolicy Bypass -File build-apk.ps1
```

产物：`NoStartupAnimation-LSPosed-v1.1.apk`（项目根目录）

## 使用

1. 安装模块 APK
2. LSPosed 管理器 → 模块 → 启用「去启动动画」
3. 作用域勾选 `com.oplus.games`（模块已通过 `xposedscope` 元数据预填）
4. 重启游戏助手进程或重启手机
5. 进游戏，启动动画不再出现

## 项目结构

```
app/                          LSPosed 模块
  src/main/java/.../MainHook.java   Hook 实现
  src/main/AndroidManifest.xml      xposed 声明 + xposedscope
  src/main/assets/xposed_init       入口类
  src/main/res/values/arrays.xml    xposed_scope 数组
build-apk.ps1                 免 Gradle 构建脚本
```
