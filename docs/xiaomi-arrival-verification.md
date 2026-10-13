# 小米到站提醒验证

日期：2026-10-13。设备：Xiaomi 23124RN87C，Android 15。
仅操作这台小米设备；没有操作同时连接的 Pixel Tablet。

## 修复

- 系统设置提前返回 Activity result 时，不提前结束授权流程；等应用实际离开并返回后检查权限。
- 用户确认定位用途后保存开启意图，Activity 或进程重建后继续通知授权及围栏注册。
- 等本次位置获取和围栏注册完成后返回开启结果，恢复生命周期不会并发重启初始化定位。
- 将应用主动的周期平衡功耗定位改成 PASSIVE 订阅；初始化及围栏更新缺少有效位置时，单次定位最多 15 秒。
- Activity 销毁时取消旧实例的权限回调，关闭或权限失效时清理位置请求和围栏。

## 实机结果

已按用户授权卸载旧签名版并安装修复后的调试版。

| 验证项 | 结果 |
| --- | --- |
| 后台位置未授权，发起一次开启请求后完成设置授权并返回 | 同一次请求返回 success / enabled / active 均为 true |
| 一次确认开启、已确认意图恢复、取消及通知回归 | 6 项通过 |
| 原应用定位订阅 | BALANCED_POWER_ACCURACY，每 5 分钟主动请求 |
| 修复后的应用定位订阅 | PASSIVE，最短交付间隔 2 分钟、移动至少 1 公里 |
| 应用 MONITOR_LOCATION | 修复前 running；修复后观察到一次 33 ms 记录，无 running |
| 北京南、香港西九龙、万象模拟到站 | 400 米外无提醒，250 米内提醒，通知跳转和同站防重复均通过 |
| Android 单元测试 / lint | 36 项通过 / 0 错误，17 警告 |
| 网页单元测试 / 提醒界面冒烟 | 108 项通过 / 开关、拒绝、重载及 320–1200 px 布局通过 |

模拟到站三站的提醒延迟约 10 秒，属于模拟位置测试结果，不代表实际旅行中的到站延迟。
结束后关闭 FLP 模拟模式、将模拟位置 AppOps 恢复为 deny，并恢复原有提醒设置和冷却记录。

Google Play 服务的 geofencer_provider 仍会为系统围栏请求位置。这是后台提醒的一部分，
不保证完全没有位置访问记录；本次没有进行长期电量基准测试。

实机证据保存在本地 `build/verification/`：

- `xiaomi-google-location-before.txt`、`xiaomi-google-location-after.txt`
- `xiaomi-settings-return-result.json`
- `xiaomi-arrival-regression-final.log`、`xiaomi-arrival-mock.log`

原网页项目的旧哈希基线另有 11 个文件差异（README 及 10 张截图），
不作为本次修复的通过条件；本次只修改 Android 项目中的网页副本。
