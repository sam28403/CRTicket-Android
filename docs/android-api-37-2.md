# Android API 37.2 适配

检查日期：2026-10-30。

## 构建配置

`app/build.gradle.kts` 使用 `release(37) { minorApiLevel = 2 }`，显式以
Android SDK Platform 37.2 编译。`targetSdk` 保持整数 API 37，`minSdk` 为 26。
本地 SDK 的 `android-37.2/source.properties` 标明 API 37.2、revision 2。

## 平台变化检查

- QPR2 优化同进程自广播的交付，不能依靠自广播保活或计时。主代码未发现
  `sendBroadcast` 调用；起售提醒使用 AlarmManager 和 PendingIntent，
  到站地理围栏使用 PendingIntent，不依赖自行发送广播保活。
- 呼叫转移 USSD 新限制：主代码未使用 `sendUssdRequest` 或 TelephonyManager。
- 内存预算是可选功能，需要先测量 release 构建的实际工作集。本次不添加未经测量的预算。

以上为源码检查，不替代后台、锁屏和系统回收后的实机回归。

## 参考

- [QPR2 发布说明](https://developer.android.com/about/versions/17/qpr2/release-notes)
- [编译 SDK 配置](https://developer.android.com/build)
- [应用内存预算](https://developer.android.com/topic/performance/memory/app-memory-budgets)

## 验证

日志统一存放于 `logs/api-37-2/`。Pixel 8 Pro 的
`ro.build.version.sdk_full` 返回 `37.2`，构建号为 `DP11.261016.003`。

- Debug APK、CI APK 与 CI 测试 APK 构建通过。
- 45 个测试类共 183 项单元测试通过，失败和错误均为 0。
- `lintDebug` 通过，报告位于 `app/build/reports/lint-results-debug.html`。
- Pixel 8 Pro 上 `TrainTrackingNotificationTest` 和
  `NavigationCompositionTest` 共 8 项测试通过。
- 导航测试补用现有 `WelcomeTestState`，避免全新 CI 安装跳转欢迎页后
  Activity 被销毁；测试后恢复原欢迎状态。
- 本次未覆盖长时间后台、锁屏闹钟、系统回收恢复及平板实机回归。

构建主日志为 `build-verified.log`，CI 构建日志为 `ci-build-final.log`，
最终实机结果为 `phone-tests-final.log`，均在上述日志目录内。
