# 应用更新

设置中的“更新”分组提供手动检查、使用预览版和自动检查更新。
使用预览版默认关闭，自动检查默认开启，设置保存在本机。
两项选择归入备份的“设置项”分类，导出为 `settings.native.updatePreviews` 和
`settings.native.updateAutomatic`，值必须为布尔类型。勾选设置项导入后立即同步设置页；
未勾选设置项、或旧备份缺少相应字段时，保留当前选择。
下载任务编号、已下载文件、待安装状态和系统安装授权不随备份迁移。
CI 构建可保存并迁移这些选择，但仍保持更新功能禁用。

正式版通过 `sam28403/CRTicket-Android` 的 GitHub Releases latest 接口检测。
开启预览版后，读取已发布的 Releases，按语义版本选择比已安装版本更新的正式版或预览版。
草稿不参与检测。已安装版本中的 Git 提交和上游日期后缀不作为预览版标识。
发布标签采用 `vX.Y.Z` 或 `vX.Y.Z-beta.N` 等语义版本格式。
当前发布流水线仍只发布正式标签；发布预览版需提供对应的预览版本构建。

启动时自动检查一次，旋转和界面重建不会重复请求。自动检查无更新或网络失败时保持静默；
手动检查通过 Toast 提示无更新或检查失败。发现更新后复用欢迎页的 Markdown 更新日志，
提供“下载更新”和“稍后更新”。首次使用协议与当前已安装版本日志保持原有行为。

下载使用系统 DownloadManager，显示系统下载进度与完成通知。
Android 10 及以上保存至公共 Downloads 目录；Android 8、9 使用应用的外部下载目录，
避免申请额外的存储权限。
下载在应用退出后由系统继续管理，完成后可从系统下载列表打开 APK，按系统提示安装。
应用不自动安装 APK。通知显示受系统下载管理器通知设置影响。
重复点击不会重复排队同一版本；失败的任务可重试。
已完成且文件仍可读取的下载会打开安装入口；文件被删除后可重新下载。
由应用打开安装入口时先检查 `canRequestPackageInstalls()`，未授权则打开系统的
“允许来自此来源”页面。用户拒绝或返回时显示提示；授权后由系统安装器确认安装。
授权变化导致系统重建安装入口进程时，会重新检查授权状态并恢复待安装的下载任务。
应用仅将 APK 交给可导出的系统安装器，并授予本次 URI 的临时读取权限，
避免第三方 APK 查看器或安装工具把更新入口变成“打开方式”选择器。
`REQUEST_INSTALL_PACKAGES` 是安装来源的特殊授权，不是下载所需的存储权限。
系统 DownloadManager 负责自己的通知，应用的通知权限被拒绝不会使下载调用抛出未处理异常。

## 构建渠道

CI 包使用独立包名 `.ci`，当前 GitHub Release 不提供同包名、同签名的 CI 更新。
因此 CI 包的三个更新设置均禁用，自动检查、手动检查、下载及安装入口也在代码中拦截，
不会通过更新功能下载正式 APK。

`updateTest` 构建使用 `.updatetest` 包名，启动器名称为 `CRTicket TEST`，
可与现有应用共存。只有该构建固定读取 `v0.9.4` 的发布日志和 APK，
不按 TEST 包的版本号过滤，用于真实下载和系统安装入口验证。
它不会将 GitHub 正式 APK 改包名或重新签名，也不能绕过系统的降级或签名校验。
设备已有较新正式包时，尝试安装 v0.9.4 仍可能被系统拒绝；实际降级前应先导出备份。

发布时需附加已签名的 APK。优先使用名称含 universal 的 APK，否则使用唯一 APK；
不选择 unsigned APK、AAB 或 APKS。缺少可用 APK 时保留更新日志并提示暂不可下载。
下载地址只接受本仓库 GitHub Releases 的 HTTPS 附件地址。

## 验证

`ReleaseVersionTest` 覆盖版本比较与下载来源检查。
`UpdateDeviceTest` 覆盖发布渠道选择、APK 选择、更新日志操作及开关默认值和保存。
CI 包运行禁用状态测试；可用的开关操作测试在 `updateTest` 变体运行。
可使用独立 CI 包进行设备测试，避免覆盖日常安装的应用：

```powershell
.\gradlew.bat :app:connectedCiAndroidTest `
    '-Pcrticket.testBuildType=ci' `
    '-Pandroid.testInstrumentationRunnerArguments.class=cc.samlab.ticket.UpdateDeviceTest'
```

真实下载测试使用 `UpdateDownloadLiveTest`，仅对 `updateTest` 构建启用。
测试从 GitHub 下载 v0.9.4，核对大小和 SHA-256，并检查拒绝安装来源权限后安全返回。
运行时需网络和至少 60MB 可用空间；可通过 `ANDROID_SERIAL` 选择单台设备：

```powershell
.\gradlew.bat :app:connectedUpdateTestAndroidTest `
    '-Pcrticket.testBuildType=updateTest' `
    '-Pandroid.testInstrumentationRunnerArguments.class=cc.samlab.ticket.UpdateDownloadLiveTest'
```

`UpdateNotificationLiveTest` 检查下载管理器通知的进度字段，测试临时下载会在结束时移除。
`UpdateInstallGrantedLiveTest` 使用已完成的下载验证系统安装器和临时 URI 读取授权，
停留在安装确认阶段后返回，不执行安装。
Android 17 可能在安装来源授权变化时结束应用进程，因此已授权分支的测试须在启动
instrumentation 前临时授权，测试结束后再恢复原来的拒绝状态，不能在测试进程内部切换授权。
