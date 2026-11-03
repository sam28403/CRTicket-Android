# 到站检测 Google 服务不可用验证

## 2026-11-03 真机结果

- 设备：OPPO PDYM20，Android 12。
- Google Play 服务已安装但被停用；测试前后均通过 `pm list packages -d com.google.android.gms` 确认，测试没有改变该状态。
- 使用 CI 离线版，包名 `cc.samlab.ticket.ci`；保留正式版 `cc.samlab.ticket`。
- 专项测试 `ArrivalUnavailableDeviceTest.unavailableServicesBlockBothSwitchesAndShowToast` 最终结果为 `OK (1 test)`，覆盖设置页与到站检测详情页两个开关。
- 两个开关均显示关闭和禁用状态；实际触摸后捕获内容与资源字符串完全一致的 Toast：“您需要启用Google服务，才可以使用此功能。”
- 点击后开启状态与待授权状态均保持关闭，详情页未弹出定位授权说明。
- 修复禁用 Switch 拦截外层点击的问题，通过独立点击层接收不可用时的触摸。

设备使用 WebView 97.0.4692.98，低于项目要求的 111；专项测试先确认版本警告后继续，没有更新或切换 WebView。此次仅验证到站检测开关与提示，不代表旧 WebView 上全部功能通过验证。未覆盖未安装 GMS 的真实设备及重新启用 GMS 后的完整定位授权流程。

## 验证记录

- 构建日志：`logs/oppo-gms-ci-build.log`。
- 安装日志：`logs/oppo-gms-ci-install.log`。
- 最终测试日志：`logs/oppo-gms-arrival-test.log`。
- 本地截图：`logs/oppo-gms-disabled-settings-toast.png`、`logs/oppo-gms-disabled-arrival.png`。
- 设备截图：`/sdcard/Android/data/cc.samlab.ticket.ci/files/tablet-native-pages/`；通过应用 `getExternalFilesDir()` 保存。

## GMS 重新启用后的启动状态回归

OPPO 重新启用 GMS 后，现场读取到精确定位和后台定位均已授权，但 `station-arrival` 中同时保存了 `enabled=true`、`registered=false`、`enableRequested=true`。旧实现启动定位时提前写入开启状态，GMS 定位或注册任务不结束时，也没有外层等待上限，造成界面在其他操作刷新后错误显示开启，并一直阻止新的开启请求。

修复后，界面仅在围栏注册成功且启动过程完成后显示开启。定位或注册失败会停止监控、清除待授权及待启动状态、立即回传完整状态并显示失败 Toast；启动超过 25 秒也执行相同处理。请求代次在完成时失效，迟到回调不能覆盖已经结束的结果。重建恢复没有原请求 ID 时，仍向原生设置同步最终结果。

最终验证使用 CI 离线版，未改动正式版或 GMS 开关：

- OPPO PDYM20 Android 12：`ArrivalActivationDeviceTest` 为 `OK (1 test)`。点击继续后，启动期间开关保持关闭，点击说明文字也不会误开启；GMS 任务未成功完成，超时后捕获失败 Toast，确认开启和待授权标记均关闭；再次点击可以重新显示开启说明并正常取消。
- Pixel 8 Pro：同一 UI 测试为 `OK (1 test)`，成功分支确认围栏已注册、功能已开启、权限可用且待授权标记已清除。
- Pixel 8 Pro：`ArrivalEnableRecoveryTest` 为 `OK (2 tests)`，覆盖单次确认只完成一次、重建后继续开启并同步原生设置结果。

日志分别为 `logs/arrival-22-activation-test.log`、`logs/arrival-18-activation-test.log`、`logs/pixel-arrival-recovery-test.log`；构建日志为 `logs/arrival-activation-build.log`。此次没有证明 OPPO 的 GMS 定位或围栏服务已恢复工作，也没有以真实到站事件验证通知触发。

## 启动加载动画

启动定位及注册围栏时，原生设置接收共享的 `starting` 状态，将开关内的关闭图标替换为旋转进度环，并暂停开关的重复点击。设置首页及到站检测详情页均使用同一开关组件；成功、失败或超时结束时同步移除进度环，显示实际开关状态。中间状态仅推送给原生设置，不提前结束网页发起的开启请求。

最终 CI 离线版验证：OPPO `ArrivalActivationDeviceTest` 为 `OK (1 test)`，覆盖详情页加载、返回设置首页同步加载、再进入详情页、超时恢复关闭与重试；Pixel 同一测试及两项恢复测试为 `OK (3 tests)`。截图已核对两个页面的进度环显示。

- 构建日志：`logs/arrival-loading-build.log`、`logs/arrival-loading-test-build.log`。
- 最终测试日志：`logs/arrival-loading-22-test.log`、`logs/arrival-loading-18-test.log`。
- 截图：`logs/oppo-arrival-loading-detail.png`、`logs/oppo-arrival-loading-settings.png`。
