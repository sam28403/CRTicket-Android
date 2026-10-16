# 手机端性能排查与验证

日期：2026-10-16。设备：Pixel 8 Pro。重点场景为页面切换、返回和列表滚动。

## 发现与修改

- 原代码每次调用 `homeColorScheme` 都重新读取动态颜色资源、构造完整配色。
  按 Resources、Configuration、深浅色和纯黑模式缓存，配置变化时重新生成，
  用弱引用避免缓存长期持有 Activity 的资源。
- 外观同步重复写入偏好、序列化并发送整套网页配色、设置系统栏。
  相同外观复用结果；真正加载新文档时重新发送，标签地址切换不清空缓存。
- 车次列表快速滚动条的边界没有变化时，跳过重复设置 padding 和整层重绘。
- release 开启 R8 优化；benchmark 继承 release，使用本地 debug 签名，
  保持非 debuggable，便于在同一手机上覆盖安装比较。
  JavaScriptInterface 方法保留原名，保证 WebView 与原生页面之间的桥接。

## 测量方法与结果

`AppFramePerformanceTest` 使用真实时间和 Window FrameMetrics，不使用 Compose
测试规则的快进时钟。改动前后各执行 200 次主题获取，总耗时从 189.93ms
降至 1.28ms。该数值只是重复主题调用的微基准，不代表应用整体提速倍数。

同一改动后的 debug APK 重跑，列表结果仍明显变化，说明 JIT、缓存和设备状态
会影响结果；不能把重复运行的全部收益归因于代码修改。

随后用 `tools/measure-app-performance.ps1` 在解锁的同一部手机上分别采样
debug 与 R8 benchmark。每页四次相同方向滑动，每次清空并读取系统 gfxinfo，
避免其有限的帧缓冲只留下最后一段滑动。以下为一轮对照中的 P95 帧耗时：

| 场景 | debug | R8 benchmark |
| --- | ---: | ---: |
| 首页滚动 | 10.47ms | 7.39ms |
| 设置滚动 | 9.73ms | 7.06ms |
| 车次列表滚动 | 16.09ms | 8.93ms |
| 车站大屏滚动 | 8.66ms | 8.95ms |
| 再次进入首页 | 10.22ms | 7.00ms |
| 再次进入设置 | 51.46ms | 12.14ms |
| 再次进入车次列表 | 36.41ms | 17.94ms |
| 再次进入车站大屏 | 27.28ms | 7.29ms |

原始结果：`build/verification/performance/cached-debug-system.json` 和
`cached-optimized-system.json`。这组数据比较的是构建方式，不能作为所有修改
前后的净收益。最后补充的文档重载去重修复另有功能回归覆盖。

本轮优化包的四个原生列表样本均未记录到超过 16.67ms 的帧，但这不等于
120Hz 下完全不掉帧，也不是完整的系统丢帧率。车站滚动 P95 没有明显改善，
首次进入车票生成页仍出现约 249ms 长帧，因此不能宣称全项目卡顿已消失。
初次测量时省电模式关闭，系统温控等级为 LIGHT；未控制温度、频率和网络，
本结果是实机场景诊断，不是严格的实验室基准。

## 复现

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
.\gradlew.bat assembleDebug assembleDebugAndroidTest assembleBenchmark --offline

# 用 adb devices -l 获取当前 transport id；先解锁手机并保持亮屏。
adb -t 16 install -r app/build/outputs/apk/debug/app-debug.apk
.\tools\measure-app-performance.ps1 -TransportId 16 -Label debug
adb -t 16 install -r app/build/outputs/apk/benchmark/app-benchmark.apk
.\tools\measure-app-performance.ps1 -TransportId 16 -Label optimized
```

优化 APK 使用本地调试签名，正式分发仍应使用项目的正式签名配置。
不在 R8 包内运行依赖未压缩内部类的 debug instrumentation；功能回归使用
debug APK，优化包通过系统采样、页面截图和入口跳转验证。

## 回归结果

- `testDebugUnitTest`：59 项通过；`lintDebug` 和 benchmark 构建通过。
- Pixel 8 Pro：主题缓存及重载、主题切换、首页导航、车站查询与返回位置、
  通知内容及目的站等 11 项回归通过。
- 车站返回采样 77 帧，最低 alpha 为 1，隐藏帧为 0。
- 单独运行真实通知栏点击测试通过：应用运行时进入上海虹桥；没有现存
  Activity 时进入北京南。不是只检查 PendingIntent 的静态内容。
- 更新了原生页面迁移后仍查找旧网页节点的首页测试，并让彩蛋断言读取
  当前字符串资源；未修改界面文案。
- WebView 重载会丢弃旧文档中的 JavaScript 回调，测试对这一正常窗口
  做有界重试，继续验证新文档的路由和主色恢复。

日志在 `build/performance-validation.log`、`build/performance-regression-pass.log`
和 `build/performance-notification-click.log`。
