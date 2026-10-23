# 原生开屏动画

Android 12（API 31）及以上使用系统 SplashScreen。MainActivity 声明启动主题，
在 super.onCreate 前切换回正常 AppCompat 主题。Android 12 以下保持原有启动方式。

## 车票成形动效

独立 AnimatedVectorDrawable 沿用蓝底白色车票，使用官方要求的 432dp 画布。

| 阶段 | 动作 | 时间 |
| --- | --- | --- |
| 票板 | 从中心横向展开，轻微过冲后回落 | 0–430ms |
| 顶部信息 | 从左向右绘制 | 190–400ms |
| 中部信息 | 从左向右绘制 | 270–480ms |
| 底部信息 | 从左向右绘制 | 350–560ms |
| 右侧竖条 | 从上方落位并显现 | 380–700ms |

每个延迟元素声明正确的初始状态，避免开场先显示完整图案再消失。
最终形态保持原有车票图标。画布尺寸遵循平台开屏规范，因此仅在此资源上豁免
普通矢量图的 VectorRaster 尺寸建议。

首帧就绪后，图标收束、背景退场与原生页面入场共用 480ms AnimatorSet 时间轴。
图标缩小至 80% 并轻转 -12 度；原生 Compose 页面从透明渐显，缩放由 97.5% 回到 100%。
只变换原生页面 View，窗口与容器背景保持原有外观配色，不再平移整个内容根容器。
采用缓入缓出曲线，在同一过渡中让主界面与开屏连续交接。
不引入额外开屏 Activity、Lottie 运行时或固定等待时间，启动快于图标动画时允许提前退场。
系统关闭动画时直接移除开屏，不修改页面透明度或位置。

启动背景跟随系统深浅色，应用内强制主题和纯黑偏好在正常页面中生效。
首页产品图标和关于 Logo 的既有行为保持原样。

## 参考

- [Google 开屏规范与官方素材包](https://developer.android.com/develop/ui/views/launch/splash-screen)
- [AndroidX 官方示例](https://android.googlesource.com/platform/frameworks/support/+/e35ddf881d671ab67b3dabf1c4bc4ce2a692d166/core/core-splashscreen/samples/src/main/java/androidx/core/splashscreen/sample/SplashScreenSampleActivity.kt)

参考官方的原生矢量动画与内容同步退场思路，车票图形和分层时间轴为本项目实现。

## 验证

2026-10-23：assembleDebug、lintDebug 成功，lint 为 0 错误、139 警告。
最后的动画初始状态调整后再次 assembleDebug 成功。
Pixel 8 Pro 覆盖安装后重新执行 MAIN/LAUNCHER 冷启动，系统报告 COLD，正常进入首页。
已查看票板展开中的空白票面、完整信息图标及首页截图，无图标裁切异常。
录屏保存在 build/ticket-splash-preview.mp4，阶段截图为 build/ticket-splash-*.png。
本次未实测 Android 12、低于 Android 12 的设备、浅色模式及关闭系统动画的效果。

### 末段微调验证

2026-10-23：缩短竖条下落行程、降低票板过冲，移除成形图标的反向上移及缩小，
改用 320ms 缓入缓出淡出和 360ms 小幅页面入场。assembleDebug 通过。
Pixel 8 Pro 冷启动报告 COLD，已抓取完整图标与退场后的首页。
录屏为 build/splash-soft-exit-preview.mp4。本次未重复运行完整 lint 和单元测试。

### 图标与主界面交接

2026-10-23：按用户指定参考手机上的 LibChecker 与 Essentials。
LibChecker 实机冷启动逐帧可见图标退场与主界面显现重叠；Essentials 实机暖启动
可见内容逐步显现，其公开 MainActivity 使用同步的图标缩小、旋转和 750ms 背景淡出。
本项目参考这种同步交接方式，独立实现 480ms 的较轻收束，车票成形时间轴保持不变。

Essentials 源码参考：https://github.com/sameerasw/essentials/blob/main/app/src/main/java/com/sameerasw/essentials/MainActivity.kt

assembleDebug 成功。Pixel 8 Pro 冷启动报告 COLD；逐帧检查确认图标退场、首页渐显与
页面缩放连续重叠，最后首页恢复完全不透明与正常大小。
录屏为 build/splash-handoff-preview.mp4，关键帧概览为 build/splash-handoff-sheet.png。
参考应用录屏仅用于本机对比，不纳入版本控制。本次未重跑完整单元测试和 lint。
