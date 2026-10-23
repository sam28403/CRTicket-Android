# CRTicket Android

CRTicket 是独立运行的 Android 车票工具，提供车票制作、历史记录、铁路查询、
余票监控、列车追踪和到站检测。网络请求、数据解析与本机数据管理均在设备内完成，
无需部署电脑后端或配置端口映射。

## 功能

- 制作车票，管理本机历史记录，导出 PNG、PDF 和 Excel。
- 查询在线车次与余票，使用路路通离线时刻表查看车次、车站大屏和路线地图。
- 监控余票变化，通过系统通知接收提醒。
- 按时刻表追踪列车行程，并在支持的系统上显示 Live Updates。
- 在授权后检测到访车站，发送到站通知。
- 使用原生 Material 界面，支持手机、平板、系统动态配色、深浅色及多语言。

车票和个人设置保存在本机。卸载应用或清除应用数据前，请先导出备份。

## 帮助

首页的“帮助”位于“设置”和“关于”之间，按功能展示常见问题、解决方法和使用规则。
页面结构和内容维护方式见 [帮助中心](docs/help.md)。

## 备份与恢复

在“设置 → 数据与备份”中导出或导入 JSON 备份，文件由 Android 系统文件选择器处理。

完整备份包含个人资料、外观、语言、到站检测开关、重要通知偏好、已保存的余票监控配置、
车迷工具开关、车次／车站／车辆查询历史、到访车站记录（首次到访时间及历史记录标记），
车速表记录（车号、统计、完整 GPS 坐标与暂停分段）、车票生成草稿，
以及历史记录中的车票。没有车票时也可以备份设置。

导入时会恢复备份中的设置，并将车票合并到现有记录中，跳过重复项、保留创建时间。
到访记录按车站合并，保留较早的到访时间及其历史记录标记，重复导入不会增加重复车站。
轨迹按记录 ID 合并，重复导入不会新增同一记录，也不会覆盖本机已有的同 ID 轨迹。
正在测速且已满一分钟的记录会导出当前快照，导入后作为历史记录，不恢复定位任务。
不足一分钟的测速记录不纳入备份。旧版完整备份缺少新增字段时，保留本机对应数据与草稿。
旧版车票备份仍可导入，且只恢复车票；完整备份导入失败时会回滚本机写入。
单文件限制为 20MB。

系统权限、通知渠道配置、离线数据缓存、临时页面输入、待导出回执和正在运行的任务不随备份迁移。
恢复到站检测偏好后，仍需满足本机定位与通知权限；余票监控恢复配置后保持停止，
由用户手动开始。

## 项目结构

| 目录 | 用途 |
| --- | --- |
| `app/src/main/java` | Android 原生界面、网络、通知及后台服务 |
| `app/src/main/res` | Android 资源与翻译 |
| `app/src/test`、`app/src/androidTest` | 单元测试和设备测试 |
| `web/src`、`web/public` | 网页业务源码、图片、字体和许可 |
| `gradle` | 构建、版本及网页资源打包配置 |
| `.run` | 可共享的 Android Studio 运行配置 |
| `tools` | 资源打包、检查和性能测量工具 |
| `docs` | 专题说明与历史验证记录 |
| `reference` | 不参与 APK 构建的电脑后端参考代码 |
| `build`、`app/build` | 被 Git 忽略的日志、报告和构建产物 |

生成的网页资源位于 `app/src/main/assets/web`，到站清单位于
`app/src/main/assets/arrival-stations.json`，均由脚本生成，不纳入 Git。
本机 SDK 路径、IDE 设备选择、依赖目录、签名密钥、数据库和运行日志也不纳入 Git。

## 开发与构建

使用 Android Studio 打开项目并完成 Gradle 同步。工具链要求以
`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties` 和
`gradle/gradle-daemon-jvm.properties` 为准；Node.js 要求见 `web/package.json`。

首次构建前安装网页依赖：

```powershell
cd web
npm ci
cd ..
```

Android 构建会自动打包网页资源。生成调试版 APK：

```powershell
.\gradlew.bat :app:assembleDebug
```

benchmark 使用 release 优化配置和本机测试签名，保持非 debuggable。
其构建会自动打包网页资源：

```powershell
.\gradlew.bat :app:assembleBenchmark
```

APK 输出位置：

- 调试版：`app/build/outputs/apk/debug/app-debug.apk`
- benchmark：`app/build/outputs/apk/benchmark/app-benchmark.apk`

### Android Studio 安装 benchmark

在运行配置下拉框选择 **CRTicket Benchmark - Install (test signed)**，连接并授权
USB 或无线调试设备，然后点击 **Run**。配置执行 `:app:installBenchmark`，自动构建网页、
生成测试签名 benchmark APK，并安装到已连接的设备；手机和平板均支持。

这是 Gradle 安装配置，不依赖 IDE 当前选中的 build variant；连接多台设备时会安装到
所有已连接且授权的设备。安装后从设备桌面启动应用。
配置文件为 `.run/Benchmark_Install.run.xml`。

### 版本规则

在 `gradle.properties` 中修改 `crticket.version` 管理语义版本。
仅在升级版本号时撰写更新日志，将上一版本以来的全部更新汇总至
`changelogs/x.x.x.md`；日常开发不持续改写版本日志，已发布的日志保持不变。
更新欢迎页会读取对应版本的 Markdown 文件，详见 [更新日志维护](changelogs/README.md)。
构建会附加 Git 提交短哈希及上游版本日期，内部版本号随提交数递增。
版本计算需要完整 Git 历史；可通过以下任务查看构建版本：

```powershell
.\gradlew.bat :app:printAndroidVersion
```

上游日期来源由 `crticket.upstreamMainView` 指定。换开发环境时，可在用户 Gradle 配置中
设置此属性，或通过 `-Pcrticket.upstreamMainView=<文件路径>` 覆盖。

## 检查

```powershell
cd web
npm run test:android
npm run test:discovery
cd ..
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleDebugAndroidTest
```

设备测试位于 `app/src/androidTest`，需要连接并授权 Android 设备。
`tools/smoke-*.cjs` 提供网页冒烟检查；运行依赖与各项验证范围见相应工具和 `docs`。
构建或检查日志请保存到 `build/logs`，避免在根目录生成临时文件。

## 权限与数据来源

到站检测需要 Google Play 服务、系统定位、后台位置及通知权限。
系统调度、定位精度和设备省电策略可能影响提醒时机。
列车追踪按时刻表推算，不代表实时位置、晚点或临时停运信息。
在线查询依赖 12306、路路通等上游服务；离线数据仅供参考，实时信息请以铁路公告为准。

车辆查询的历史车号、车次关联数据来自 [rail.re](https://rail.re/)，展示接口返回的全部记录，
历史覆盖范围受上游限制。车次卡片使用路路通对应日期的时刻表摘要，缺失字段显示为不可用。
车型车头图与首页轮播素材来自[中国动车组车型目录](https://www.china-emu.cn/Trains/ALL/)，
按车型对应关系加载。相关实现及数据边界见 [车辆查询说明](docs/vehicle-query.md)。
按车号区分头型和版本的规则及本次核对记录见 [图片核对清单](docs/vehicle-artwork-audit.md)。

## 许可

照片沿用原项目 CC BY 4.0 授权，作者为 @Sam28403，见 `web/LICENSE`。
第三方数据、字体与素材许可见 `web/public/licenses`。
