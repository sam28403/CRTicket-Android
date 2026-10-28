# 清理存储

入口为设置中的“清理存储”，使用现有 Compose 设置三级页面、Material 3 Expressive
主题和圆角分组组件。

首版按实际文件大小显示可清理空间，提供单项清理和全部清理，操作前确认。

| 类别 | 范围 | 清理后的行为 |
| --- | --- | --- |
| 网页与地图缓存 | WebView HTTP 资源缓存 | 由 WebView 清理，后续访问重新下载 |
| 路路通离线数据库 | IndexedDB 中 `crticket-phone-timetable/archives/current` 数据包 | 清理磁盘与加载器内存缓存，下次查询联网重新下载 |
| 铁路线路缓存 | `cache/railway-lines` | 查询线路时重新获取 |
| 分享图片 | `cache/quiz-share` | 删除生成的答题分享图片 |
| 导出临时文件 | `cache/export-*.tmp` | 删除导出操作留下的临时文件 |
| 测试截图 | 当前包的 `Android/data/<包名>/files` 中已知测试输出位置的图片 | 删除后隐藏此项，也包含在全部清理中 |

“个人数据与离线资源”为应用私有 files、SharedPreferences 和 WebView 持久化数据
的文件大小合计，排除已单列的 HTTP 缓存及路路通数据包字节数。此项仅展示，不提供删除。
路路通数据库显示保存的数据包大小，IndexedDB 的索引、日志等额外开销仍计入其余数据。
保留车票、提醒、到访记录、测速记录、答题记录、个人信息与偏好设置。
全部清理也包括已下载的路路通数据库；确认框会说明下次查询需要联网下载。
单独清理其他类别时仍保留路路通数据库。
不会删除下载目录中的安装包或用户已导出的备份，不会重置应用。

测试截图仅在找到对应文件时显示；空目录不显示，零字节的截图文件仍可清理。
识别范围在 `StorageTestScreenshots.kt` 中维护，覆盖 `tablet-native-pages`、
`expressive-*-scenes` 等已有测试目录及 `welcome-terms.png` 等单独输出文件。
目录内仅清理 PNG、JPEG、WebP 图片，不清理同目录中的 JSON、CSV、文本日志或备份，
也不匹配未知目录中的个人图片。扫描不跟随符号链接。
每个安装版本只能管理自己的外部私有目录；CI 版管理 `cc.samlab.ticket.ci`，
正式版管理 `cc.samlab.ticket`，不申请额外存储权限。正式版历史测试截图需更新正式版后清理。

当前数字为上述管理范围的文件大小，不是系统设置中的应用总占用，
不含 APK、运行时编译文件或仅存在于内存中的查询结果。
WebView 在页面使用期间可能继续生成资源缓存，清理后的占用也可能重新增加。
网页与地图缓存的显示取决于设备 WebView 的标准磁盘目录布局。
文件扫描和清理在后台执行，WebView 清理接口在主线程调用。
路路通数据通过 WebView IndexedDB 事务精确删除，不直接操作浏览器数据库目录。
清理会中止正在下载的数据包，等待已有读取和下载结束后再删除，防止并发任务写回旧数据。

界面文案放在各 `res/values*/strings.xml`；简体、繁体中文使用相应文案，
其余语言本次新增项暂用英文。

设备回归：`StorageSettingsDeviceTest`，使用 CI 独立包，验证入口、取消、单项清理、
全部清理、保留数据和返回设置；截图保存在 CI 包外部文件目录的 `tablet-native-pages`。

## 2026-10-28 首版验证

- Pixel 8 Pro，独立包 `cc.samlab.ticket.ci` 安装成功，正式包仍保留。
- `StorageSettingsDeviceTest`：`OK (1 test)`。
- 取消后测试文件仍存在；单项清理只删除线路缓存；全部清理删除分享和导出临时文件。
- 私有保存文件、未归类的缓存测试文件、WebView localStorage 标记均保留。
- 172 项 CI 单元测试全部通过，无失败、错误或跳过。
- 浅色真机截图已检查，位于 `captures/storage-cleanup`，初始截图包含临时测试缓存。
- 清理后 WebView 仍有约 1.3 kB 的缓存目录索引，保留数据约 3.5 MB。
  本次未验证深色视觉、其他 WebView 版本或真实平板布局。

## 路路通数据库清理补充验证

- CI 主包及测试包重新构建并安装到同一 Pixel 8 Pro。
- 路路通加载器、车站和其他铁路数据相关的 42 项 JavaScript 测试通过。
  新增检查覆盖清理后重新下载、初始化期间清理、下载期间清理，防止旧数据写回。
- 真机数据库专项使用临时 8192 字节数据包验证占用展示、取消保留、确认删除，
  并确认 localStorage 个人数据标记仍存在；专项结束后恢复其开始前的数据包。
- 首轮原有缓存测试在启动等待窗口焦点时超时，数据库专项通过。
  随后完整重跑原有缓存及数据库两项测试，最终为 `OK (2 tests)`。
- 日志：`build/logs/storage-timetable-device-tests-retry.log`。
  截图：`captures/storage-cleanup/timetable-overview.png` 和 `timetable-confirmation.png`。

## 测试截图清理补充验证

- Pixel 8 Pro 的 CI 版已更新，三项存储设备回归最终为 `OK (3 tests)`。
- 截图专项验证取消保留、单项清理、全部清理、零字节文件可清理，
  以及清理后和重新进入页面后隐藏该项。
- 同目录 JSON 测试文件和外部目录中的普通图片在清理过程中保留。
- 174 项 CI 单元测试通过，包含历史目录识别、嵌套截图、个人图片排除和空目录检查。
- 设备测试结束后检查 CI 包外部 files 目录，已无残留文件。
  截图专项不再生成新的截图，避免验证完成后重新出现该项。
- 日志：`build/logs/storage-screenshot-device-tests.log`。
