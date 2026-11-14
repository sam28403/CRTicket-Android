# CRTicket 文档

## 开始使用

应用内可从首页打开「帮助」，按功能查找常见问题。首次安装、更新或反馈问题时，可参考以下说明。

- [帮助主题与内容维护](help.md)
- [应用更新](app-updates.md)
- [欢迎页与更新说明](welcome-release-notes.md)
- [WebView 兼容性](webview-compatibility.md)
- [错误报告与功能反馈](issue-reporting.md)
- [异常报告](failure-reports.md)

## 铁路查询

从首页搜索进入车次、车站和线路。组合查询可从对应功能入口填写日期、起讫站等条件。

- [首页全局搜索](global-search.md)
- [搜索与车次查询导航](search-query-navigation.md)
- [车次列表排序](train-list-order.md)
- [车次开行日历](train-operating-calendar.md)
- [正晚点查询与缓存](train-delay.md)
- [车次调向标签](train-turn-stations.md)
- [新旧车次对比](train-number-comparison.md)
- [车站大屏分段车次](station-train-segments.md)
- [中老铁路老挝段时刻表](lcr-timetable.md)
- [铁路线路查询](railway-line-query.md)
- [车辆查询与实拍照片](vehicle-query.md)
- [起售时间查询与提醒](sale-time-query.md)

## 追踪、地图与车迷工具

- [列车追踪正晚点同步](train-tracking-delay-refresh.md)
- [地图源与轨迹显示](map-sources.md)
- [车速表与历史轨迹](speedometer.md)
- [普速参考票价](ordinary-fare.md)
- [每日小测验](quiz.md)

## 备份与存储

更换设备或清除应用数据前，可在「设置 → 数据与备份」导出备份。

- [备份与恢复、密码及文件格式](backup-format.md)
- [可备份的数据与导入规则](backup-data-coverage.md)
- [备份版本兼容](backup-version.md)
- [清理存储](storage-cleanup.md)

## 开发与发布

- [自动构建与正式发布](releases.md)
- [Android 文案与翻译维护](i18n.md)
- [本地日志管理](logs.md)
- [Android API 37.2 适配](android-api-37-2.md)
- [原生开屏动画](native-splash-screen.md)
- [地图服务接入调研](map-provider-options.md)
- [上游普速票价计算器](upstream-price-calculator.md)
- [车辆车头图核对清单](vehicle-artwork-audit.md)
- [车辆车头图核对数据（CSV）](vehicle-artwork-audit.csv)
- [开源致谢](open-source-credits.md)

## 历史验证

这些记录对应各自的测试日期、构建和设备。

- [到站检测模拟位置](arrival-mock-verification.md)
- [Google Play 服务不可用时的到站检测](arrival-gms-verification.md)
- [小米设备到站提醒](xiaomi-arrival-verification.md)
- [后台监控与重启恢复](background-monitor-verification.md)
- [列车追踪自动退出](train-tracking-expiry-verification.md)
- [单侧晚点追踪与车次刷新](train-delay-tracking-device-verification.md)
- [正晚点缓存](delay-cache-device-verification.md)
- [车站大屏紧凑布局与网格](station-adaptive-verification.md)
- [系统胶囊五字站名](station-chip-width-verification.md)
- [原生车次列表](expressive-train-list-verification.md)
- [车速表与定位路线](speedometer-device-verification.md)
- [平板原生页面布局](tablet-native-pages-verification.md)
- [原生设置与页面主题](native-appearance-device-validation.md)
- [页面切换动画](navigation-motion-validation.md)
- [手机端性能](performance-verification.md)
- [代码整理](code-refactor-verification.md)
- [上游 261028 车次规则](upstream-261028-device-verification.md)
