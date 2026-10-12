# 系统胶囊五字站名验证

2026-10-12，在 Pixel 8 Pro（Android 17，API 37）实机验证。
设备分辨率为 1344 × 2992，当前显示密度为 544 dpi；未调整屏幕、字体或系统设置。

使用 `TrainStationChipWidthProbeTest` 构建停站通知，仅在测试包中覆盖
`shortCriticalText`，对照四字和五字站名。正式应用的显示逻辑保持不变。
测试结束后恢复原列车追踪，未替换手机上的正式应用。

| 请求文字 | 实际胶囊显示 |
| --- | --- |
| 乌鲁木齐（四字） | 定位图标和四个汉字完整显示 |
| 乌鲁木齐南（五字） | 仅显示定位图标，文字消失 |
| 上海虹桥站（五字） | 仅显示定位图标，文字消失 |

系统通知中的 `shortCriticalText` 确实收到完整五字字符串，
但 SystemUI 未提供文字节点，截图也确认仅剩图标。
四字对照的文字节点为 `ongoing_activity_chip_content`，宽度为 208 px。

结论：当前实机配置无法完整显示五个汉字，继续使用四字窗口，
较长站名在亮屏时每 3 秒切换窗口；熄屏时保持原有更新策略。
这个结果限定于本次设备和显示配置，不推断所有 Android 设备都相同。

截图和界面文字位于忽略的构建目录：
`build/verification/chip-width/tracking-chip-width/`。
五字实验只验证通知能提交和实际显示效果，不能将测试通过解释为五字可见。

官方参考：[Android Live Updates 状态胶囊](https://developer.android.com/develop/ui/views/notifications/live-update)。
官方给出最大宽度 96dp，实际可见内容取决于文字能否放入。
