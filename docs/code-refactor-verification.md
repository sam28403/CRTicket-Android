# 代码整理验证

2026-10-11。保持整理前的功能、界面、权限流程和数据格式。

## 共用逻辑与模块

- `web/src/android/nativeCalls.js` 共用桥接回调、超时、取消和资源清理。
  HTTP、文件保存、余票通知、列车追踪、到访提醒保留原有各自的超时策略。
- `web/src/android/nativeNotifications.js` 共用通知桥接调用，保留原导出兼容入口。
- `web/src/utils/requests.js` 共用取消错误和共享查询的等待逻辑。
- `NotificationAccess.kt` 共用三类通知的权限及渠道检查。
- `ArrivalLocations.kt` 共用位置时间、精度检查，保留原有判定条件。
- `MainActivity.kt` 分离 `AndroidBridge`、`DocumentFiles`、`LocalAssetClient`、
  `WebViewLayout`，保留生命周期、外观和通知跳转协调。

手写 Kotlin、JavaScript、Vue 源码及 Android 实机测试中的代码行不超过 120 字符。
坐标表等静态数据文件不参与代码格式化。
路路通更新缓存逻辑、到访范围、定位频率、通知文案、页面模板及样式均未修改。
保留整理前工作区已有的 Material 权限弹窗改动和依赖配置。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| JavaScript 回归 | 83 项通过 |
| Android 单元测试 | 27 项通过 |
| Android 编译和 lint | 构建通过，lint 0 错误 |
| 浏览器界面回归 | 首页、主题、列车追踪、大屏、到访提醒均通过 |
| Pixel 8 Pro 实机回归 | 6 项通过，Android API 37 |
| 用户数据与设置 | 实机测试前后车票、用户信息、主题及 Android 设置一致 |
| 权限及模拟定位设置 | 测试前后保持一致 |

实机覆盖桥接方法注册、按需加载通知回调、车站／余票监测／车次通知跳转、
网页资源来源限制、响应头、权限弹窗取消和确认、站点清单和通知内容。
实机测试不申请权限、不发布测试通知、不写入用户车票或设置。

实机源码：`app/src/androidTest/java/cc/samlab/ticket/RefactorReadOnlyTest.kt`。
完整日志：`logs/archive/build/verification/pixel8pro-refactor-tests.log`，`OK (6 tests)`。
JavaScript 日志：`logs/archive/build/verification/refactor-js-tests.log`。
Android 构建日志：`logs/archive/build/verification/refactor-native-final.log`。

不执行会改动设备设置或通知状态的定位模拟、主题切换及列车追踪探针。
对应业务流程在独立浏览器测试数据中回归。
