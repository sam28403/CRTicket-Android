# 错误报告

从首页进入「关于」，点击 GitHub 图标右侧的「报告错误」进入三级页面。
支持错误报告、功能建议、使用问题；填写标题和描述后，前往 GitHub
确认并发布。用户需要登录 GitHub，截图可以在 GitHub 编辑器中添加。

报告包含应用版本；机型和 Android 版本由用户选择是否附带，默认关闭。
页面提供完整正文预览和复制。较长报告完整复制到剪贴板，并提示用户在
GitHub 正文中粘贴，避免过长链接或正文截断。草稿保留在当前导航页面的
保存状态中，返回离开页面后结束。

实现参考：

- [HyperBridge 技术栈](https://github.com/D4vidDf/HyperBridge#-tech-stack)：Kotlin、Jetpack Compose、Material 3 Expressive。
- [错误报告页面功能说明](https://github.com/D4vidDf/HyperBridge/discussions/302)。
- [BugReportScreen.kt](https://github.com/D4vidDf/HyperBridge/blob/dev/0_6_0/app/src/main/java/com/d4viddf/hyperbridge/ui/screens/settings/BugReportScreen.kt)。
- [BugReportCollector.kt](https://github.com/D4vidDf/HyperBridge/blob/dev/0_6_0/app/src/main/java/com/d4viddf/hyperbridge/util/BugReportCollector.kt)。

参考其结构化描述、复现步骤、可选设备信息、GitHub 报告流程，按 CRTicket
现有主题与原生导航独立实现，未引入外部 SDK 或复制源文件。
