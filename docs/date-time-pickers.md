# 日期与时间选择器

## 操作方式

- 历史记录、车次查询、车次列表、余票监控和 CI 版生成车票共用日期弹窗。默认打开完整月历，点顶部日期右侧的图标可切换到年月日滚轮，再点日历图标可返回。各入口沿用原有可选日期范围。
- 补登和编辑历史记录时，时间弹窗默认显示 24 小时时分滚轮。点键盘图标可直接输入小时和分钟。
- 年月日、时分滚轮的数字和月历顶部的完整日期使用 Google Sans Rounded。月历的日期格、星期栏和年份列表使用默认字体。
- 快划松手后，滚轮会继续转动、逐渐减速，并吸附到一个数字。滚轮跨过刻度时会请求系统轻触感。反馈取决于设备硬件与系统触感设置，短时间内的多次反馈可能合并。

## 实现

年月日和时分共用 `PickerWheel`。它使用 Compose `VerticalPager` 和居中吸附，保留 Material 3 `ScrollFieldDefaults` 的配色和形状。单次甩动的停靠距离限制为 12 格。滚轮通过 `SegmentFrequentTick` 请求轻触感。

日期滚轮停稳后才更新顶部日期和选中值。切回月历时同步显示选中的月份。时间滚轮在确认或切换输入方式时读取当前小时和分钟，避免滚动中反向重定位。

## 真机验证

2026-11-18 在 Pixel 8 Pro 和 Pixel Tablet 上运行 `PickerScrollPerformanceTest#reportDateAndTimeWheelFrames`。两台设备在短距离快划后，日期滚轮均前进 5 格，时间滚轮均前进 3 格。测试同时核对了停靠值、日期切回月历、时间切换到键盘，以及确认回填的结果。

Pixel 8 Pro 的系统振动记录确认播放了轻触感。Pixel Tablet 的振动服务未检测到振动器，将对应请求标记为不支持。

本地日志位于 `logs/picker-fling-ticks-Pixel_8_Pro.log`、`logs/picker-fling-ticks-Pixel_Tablet.log`，以及对应的 `logs/picker-fling-ticks-vibrator-*.log`。
