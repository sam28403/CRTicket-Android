# 欢迎页与本次更新

首页左上角的应用图标和 CRTicket 标题共用点击区域，点击后直接打开
Welcome Screen 的“本次更新”页，展示当前版本打包的 Markdown 更新日志。
“本次更新”标题使用项目内置的 Google Sans Rounded。

从首页打开时，底部按钮和系统返回键均关闭更新页，返回已有首页，保留其状态。
此入口不修改首次使用条款或版本确认记录。首次安装和升级时的欢迎流程保持独立。

设备回归测试位于 ExpressiveHomeTest 的
homeTitleOpensReleaseNotesAndReturnsToTheSameHome，覆盖打开、按钮返回与系统返回。

2026-10-24：Debug 应用和测试 APK 构建成功，116 项 JVM 单元测试通过，
Lint 为 0 错误。Pixel 8 Pro 首轮标题入口及返回测试通过；补充图标触控位置、
截图及原欢迎页回归时，设备处于安全锁屏状态，WelcomeActivity 被遮挡，
测试未通过。补充设备验证需在解锁状态下进行。
