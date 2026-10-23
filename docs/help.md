# 帮助中心

首页工具分组按设置、帮助、关于排列。帮助使用原生 Compose Material 3 Expressive，
复用设置标题栏与分组卡片，以及原生导航栈、返回动画、主题和宽屏侧栏。
二级页列出功能，三级页按常见问题和使用规则展示正文。

覆盖车票和历史、车次车站查询、备份恢复、到站检测、余票监控与列车追踪、
起售预约、测速轨迹、车辆查询、外观与问题反馈。规则来自当前实现，
不将重要通知中的计划车次规则作为已启用行为。

内容维护在各语言目录的 strings.xml，主题与路由映射在 ui/help/HelpTopics.kt。
当前提供简体中文和英文；其他语言回退英文。新增功能或变更限制时应同步更新帮助。
正文依据 README、backup-data-coverage.md、sale-time-query.md、speedometer.md、
vehicle-query.md、background-monitor-verification.md、train-tracking-expiry-verification.md，
以及 ticketNumericValidation.js、trainNumberRules.js、ticketValidation.js 校验逻辑。

## 验证

2026-10-23：debug APK 与设备测试 APK 构建成功，116 项 JVM 单元测试通过。
Pixel 8 Pro 的 helpTopicsReturnToIndexInTheNativeStack 实机测试通过，覆盖
帮助目录、车票／备份／测速详情、规则区块和逐级返回。宽屏子页面保留父级路径由
WideNavigationPolicyTest 覆盖，未执行平板实机视觉检查。

完整串行检查 assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug 成功。
