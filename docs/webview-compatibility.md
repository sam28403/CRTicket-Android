# WebView 兼容性

当前将 Chromium WebView 111 作为最低支援警告线。这是按代码依赖推导的门槛，尚未经过旧 WebView 实机验证，也不保证所有地图和硬件功能可用。此前分析记录未找到，本次重新核对。

- 前端构建目标为 ES2019；它不会自动补齐运行时 API。
- 当前使用 Array.at、crypto.randomUUID、Array.findLast 和 CSS color-mix。
- color-mix 从 Chromium 111 起支持，参考 [Chrome 官方文档](https://developer.chrome.com/docs/css-ui/css-color-mix)。

Android 原生层读取系统当前实际启用的 WebView 提供程序版本，不按已安装的 Chrome 或其他未启用提供程序判断。主版本低于 111 时展示原生警告，列出当前版本与最低版本，确认后继续使用。旋转或语言重建不重复提示；重新启动 Activity 时重新检查，不提供永久忽略。版本无法读取或解析时不误报为低版本。

原生与网页的旧“重要通知”启动入口均已移除；历史备份中的忽略偏好保留兼容读取，不影响新警告。

验证计划：低版本设备启动应弹窗；确认后可继续；旋转不重复；重新打开应再次提示；111 及以上不提示。旧设备、地图 GPU 能力与弹窗视觉待实机验证。

本次验证：Debug APK 构建成功；WebViewVersionPolicyTest 两项测试通过，覆盖 110/111 边界、新版本及未知版本；全部语言资源 XML 与警告键检查通过。未运行设备测试。
