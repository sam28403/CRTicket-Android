# CRTicket-Android — 手机独立版

后端逻辑已内置 APK。手机通过 Android 原生网络层直接连接 12306 和路路通，查询、解析、缓存及车票数据处理都在手机内完成。无需电脑、远端后端、端口映射或额外的运行环境。

## 使用

安装 `app/build/outputs/apk/debug/app-debug.apk` 即可。应用默认使用“默认用户”，可在“数据管理”中修改用户名；没有登录、注册或后端地址设置，顶部实现说明栏已移除。

- 车票制作、历史增删改、头像、统计、地图、PNG/PDF/Excel 导出和 JSON 备份都使用手机本机数据。
- 余票与在线车次查询由手机直接请求 12306。
- 列车列表及离线时刻表由手机下载路路通数据包，在 Web Worker 中解析，并保存到本机 IndexedDB。后续可使用已下载数据，联网时检查更新。
- 经典与高对比度的“跟随系统”直接读取 Android 夜间模式，并在系统切换或回到前台时更新；手动亮色、暗色选择保持独立。
- 原有手机离线车票记录保留。卸载应用或清除应用数据会清除记录和时刻表缓存，请通过导出备份保存重要数据。
- 文件导入、保存由 Android 系统文件选择器处理，无需存储权限；单文件导出上限 20MB。
- 余票监控在开始时申请系统通知权限，有票或余票变化时发送“余票提醒”通知；点击通知可返回监控页面。通知被关闭时可前往系统设置开启，未授权时保留页面内提醒。监控仍采用前台轮询，手机锁屏或应用进入后台可能延迟，没有持续后台服务。

12306 或路路通上游服务的验证、限流、接口调整及网络故障会影响在线查询；应用会展示错误。缓存数据的版本会在查询结果中显示，离线参考不能代替 12306 实时开行和余票信息。

最低 Android 8.0（API 26），设备需要支持现代 JavaScript 的 Android System WebView / Chrome。

## 模块

- `app/src/main/java/cc/samlab/ticket/network/PhoneHttp.kt`：Android 原生 HTTP、Cookie、取消请求与二进制分块传输。
- `app/src/main/java/cc/samlab/ticket/network/UpstreamPolicy.kt`：只接受所需的 12306、路路通数据地址。
- `web/src/android/railway`：车次、余票、列车列表、路路通二进制解析、IndexedDB 缓存和解析 Worker。
- `web/src/android/localApi.js`：默认用户和本机车票操作。

保留前一版启动优化：页面、图表和导出库按需加载；手机票面实时等比显示；字体使用精简 WOFF2；静态资源可缓存，升级清理资源缓存时保留本机数据。


12306 的 CFCA 信任证书限定用于 search.12306.cn 和 kyfw.12306.cn；证书链、有效期和主机名验证仍生效。证书取自 Node.js 内置 Mozilla 信任库，SHA-256 为 5C:C3:D7:8E:4E:1D:5E:45:54:7A:04:E6:87:3E:64:F9:0C:F9:53:6D:1C:CC:2E:F8:00:F3:55:C4:C5:FD:70:FD，与 [CFCA 官方技术手册](https://www.cfca.com.cn/upload/CFCA%E5%85%A8%E7%90%83%E4%BF%A1%E4%BB%BB%E8%AF%81%E4%B9%A6%EF%BC%88SSL%E8%AF%81%E4%B9%A6%EF%BC%89%E6%8A%80%E6%9C%AF%E6%89%8B%E5%86%8Cv5.1.pdf) 一致。

## 构建和检查

版本名称格式为 `<x.y.z>-<当前提交的至少 7 位短哈希>-<原项目版本日期>`。
`x.y.z` 由你手动修改根目录 `gradle.properties` 中的 `crticket.version`，默认 `0.3.3`，不会随提交自动变化。
日期在构建时自动读取 `crticket.upstreamMainView` 指向的原项目 `MainView.vue` 中的 `<h3>Version 260930</h3>`。
默认文件为 `C:/Users/SAM-LAB/WebstormProjects/CRTicket/src/views/MainView.vue`；换电脑时可修改该路径，原文件更新后构建缓存会自动刷新。
文件缺失或没有唯一的版本日期时构建会报错，不会使用旧日期或本机日期。
Android 内部 `versionCode` 从 7 起随提交次数递增，支持覆盖升级此前的版本；它不影响手动设置的 `x.y.z`。
尚无提交时哈希标记为 `uncommitted`；构建需要完整 Git 历史。
可运行 `.\gradlew.bat :app:printAndroidVersion` 查看实际版本，或运行 `tools/verify-versioning.ps1` 验证版本配置、日期更新和缓存刷新。

网页资源已生成，Android Studio 可直接运行 app。修改网页副本后重新构建：

```powershell
cd web
npm ci # 首次安装依赖时使用
npm run test:android
npm run build
cd ..
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

使用项目原有 AGP 9.5.0-alpha09、Gradle 9.6.0、SDK 37 和 Java 25 工具链。Kotlin 编译器 2.5.0-Beta1，运行时 2.2.10。

`tools/smoke-web.cjs` 检查手机版布局、本机历史和四种导出，需要 Playwright 和 Chrome。`PhoneBackendTest` 是 Android 真机测试，验证在没有电脑后端与端口映射的情况下，手机加载路路通数据并访问 12306。生成测试包：

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
```

字体工具位于 `tools/optimize-fonts.py`，只处理当前 Android 项目的副本。所有原网页文件均保持只读，可执行下列校验：

```powershell
.\tools\verify-web-source.ps1
```

## 旧电脑部署

此前为本项目启动的电脑服务已停止，Pixel 8 Pro 的 3000 端口映射已移除；Pixel Tablet 未改动。旧电脑代码和数据库保留在 `reference/desktop-server`，不参与 APK 构建。手机独立版使用手机本机历史，旧电脑数据库没有自动导入。

照片沿用原项目 CC BY 4.0 授权，照片作者：@Sam28403；原授权文本见 `web/LICENSE`。源网页项目 `C:\Users\SAM-LAB\WebstormProjects\CRTicket` 未修改。
