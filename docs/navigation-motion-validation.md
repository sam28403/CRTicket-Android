# 页面动画的系统对照验证

参考设备：Pixel 8 Pro，Android 17，构建 `DP11.260918.006`。
对照时系统 `transition_animation_scale` 和 `animator_duration_scale` 均为 `1.0`。
参考对象为系统设置和 Essentials 的完整页面切换。

## 速度差异与修正

原实现将预测返回映射到 Navigation 的可寻址动画时间轴。
手势结束后播放剩余时间，会随着拖动进度改变收尾速度。
线性预览也与系统的减速曲线不同。

普通进入、普通返回读取设备 framework 的四个 Activity 动画，按毫秒采样。
预测返回使用独立阶段：

- 拖动：系统 BackGestureInterpolator `(0.1, 0.1, 0, 1)`，保持前景不透明。
- 松手完成：从当前几何状态重新计时，完整执行 450 毫秒收尾。
- 收尾：先应用 ValueAnimator 默认插值，再应用系统 emphasized 曲线。
- 取消：临界阻尼弹簧，stiffness 为 1500，不使用固定剩余时长。
- 甩动：按手势事件时间戳，通过 VelocityTracker 测量插值后进度的速度。

返回栈和每个条目的 Lifecycle、ViewModel、SaveableState 仍由 AndroidX Navigation 管理。
动画计时单独管理，避免普通返回与预测返回互相改写曲线或缩短时长。

## 自动检查

`SystemMotionParityTest` 对照设备 framework：

1. 四个普通 Activity 动画逐毫秒比较位置、缩放和透明度。
2. 比较系统预测返回减速曲线。
3. 比较短拖动和长拖动的完整收尾时长及系统插值结果。

`ExpressiveNavigationTest` 检查返回栈、子页、左右边缘、取消后的几何恢复，
以及不同拖动进度松手后仍执行完整收尾。
同时检查进入动画未结束就立即返回时的页面状态交接。
另以历史编辑器返回和宽窄窗口切换测试检查草稿及页面状态保留。

实机录屏使用相同的 1.2 秒边缘手势和操作间隔。
数值对照针对上述 Pixel 系统版本；录屏用于复核实际播放过程。

最终验证：63 项单元测试、12 项实机测试通过，lint 无错误。

## 参考实现

- [AOSP DefaultCrossActivityBackAnimation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/WindowManager/Shell/src/com/android/wm/shell/back/DefaultCrossActivityBackAnimation.kt)
- [AOSP BackProgressAnimator](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/window/BackProgressAnimator.java)
- [Essentials](https://github.com/sameerasw/essentials)
