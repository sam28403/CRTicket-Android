# 后台监控与重启恢复验证

2026-10-13。修复版 APK：`0.4.3-22a3e4b-261013`，versionCode 17。

余票监控改由独立 Android 前台服务查询 12306，与 Activity、WebView 及页面订阅的生命周期分离。
切到后台、锁屏和离开监控页面时保留任务；页面再次打开会恢复查询条件、筛选、结果及提醒记录。
网络或上游查询失败后保留开启状态，按配置间隔重试。通知提供停止按钮，显式停止后不自动恢复。
查询日期过期时结束任务并移除后台监控通知。

车次监控在 Activity 返回前台时恢复保存的时刻表和原会话，保留原截止时间。
到达时刻表终到时间后不重启追踪服务；原有终到通知沿用原清除期限。
显式停止会清除保存的行程。终到通知状态读取不重复发布通知，避免覆盖系统的超时清除安排。

| 设备 | 系统 | 本次操作 |
| --- | --- | --- |
| Pixel 8 Pro | Android 17 / API 37 | 覆盖安装修复版；后台、锁屏、页面操作、关闭重开及通知回归 |
| Xiaomi 23124RN87C | Android 15 / API 35 | 同样覆盖安装与验证 |

测试没有操作同时连接的 Pixel Tablet。覆盖安装保留应用数据，测试使用临时行程和查询条件，
结束后恢复两类监控原有的完整偏好记录及会话截止时间。

| 检查 | 结果 |
| --- | --- |
| Android 单元测试 | 41 项通过 |
| 网页单元测试 | 124 项通过 |
| 网页资源、应用 APK、设备测试 APK | 构建通过 |
| Android lint | 0 错误，21 警告 |
| Pixel 普通实机回归 | 26 项全部通过 |
| Xiaomi 普通实机回归 | 26 项通过；同步调整后的 4 项过期测试单独复验 |
| 两机真实进程重启 | 未终到恢复、已终到不恢复、显式停止不恢复，全部通过 |

普通实机回归包含 4 项车次生命周期、4 项余票生命周期、5 项余票数据处理、2 项真实 WebView
页面操作、4 项自动退出及 7 项通知测试。余票后台测试访问真实 12306，以原生查询尝试次数持续
增长判断后台任务没有停止，允许官网暂时失败；并不把上游每次查询成功作为通过条件。
WebView 测试还实际点击开始和停止按钮，检查导航返回和页面重载后的状态。

进程重启测试分成准备和验证两次 instrumentation，中间真实执行 `am force-stop` 和启动应用。
验证新旧进程 ID 不同、车次会话和截止时间不变、余票恢复查询，随后还原测试前的监控记录。
可通过以下脚本重复运行；transport ID 必须先用 `adb devices -l` 检查，脚本会核对设备型号。

```powershell
.\tools\test-monitor-process-restart.ps1 -TransportId 13 -ExpectedModel Pixel_8_Pro
.\tools\test-monitor-process-restart.ps1 -TransportId 12 -ExpectedModel 23124RN87C
```

本次发现并修复终到通知被恢复路径及排队时钟广播重复发布的问题；另调整过期测试的准备阶段，
在同一个主线程任务中写入过期记录并读取状态，避免正常的 Activity 恢复提前清理测试样本。

完整证据位于本机 `build/verification/`：`monitor-build-final.log`、`monitor-test-build-final.log`、
`pixel-monitor-regression-final.log`、`xiaomi-monitor-regression-final.log`、`xiaomi-monitor-expiry-final.log`，
以及两设备 `*-process-active-*.log`、`*-process-finished-*.log`。

终到判断沿用时刻表，未接入实时晚点信息。本次属于短时后台及锁屏回归，没有进行长期深度休眠
或耗电基准测试。前台服务的恢复遵守 Android 系统规则：系统强制停止期间不执行，重新打开后恢复。
