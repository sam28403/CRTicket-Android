# 原生 Expressive 车次列表预览

2026-10-15，仅在 Pixel 8 Pro 上安装、验证，保留设备现有显示及字体缩放。

## 页面

- 原生 MaterialExpressiveTheme、动态配色及亮暗色偏好与首页一致。
- 二级页面标题为“车次列表”；标题下仅有日期选择器与总趟数。
- 原生 Material 日历选择始发日期；继续使用手机内的路路通查询与缓存。
- 圆角列表项保留车次、始终站、历时、站数、里程及均速；点击进入原有详情页。
- 顶部下拉刷新采用原生 Expressive 变形指示器。

## 长列表

使用单个纵向 LazyColumn，没有横向滚动容器。行有稳定键及统一 contentType，
仅组合视口附近的条目。完整数据的 JSON 解析和展示文本格式化在后台执行，
滚动不经过 WebView 桥接；Android 下不再同时渲染隐藏的网页虚拟表格。
页面切换保留滚动位置，切换日期回到顶部，旧数据回调不能覆盖新快照。

## 验证

- `npm run test:android`：133 项通过。
- `npm run build`、`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、
  `:app:lintDebug` 通过；lint 没有错误。
- `ExpressiveTrainListTest` 最终两项设备测试通过（17.609 秒）：真实数据、日期弹窗、
  切换次日、下拉刷新、详情跳转；20,000 条数据的横向手势、快速纵向滑动、
  返回位置恢复与末条可达。
- 当天真实列表为 12,085 趟。20,000 条压力数据仅用于测试活动，不修改保存记录。
- 压力测试断言组合条目少于 30 个；未以 debug 构建的结果推断发布版帧率。
- 截图位于设备应用目录 `files/expressive-train-list/`。

浏览器首页冒烟测试因本机缺少 Playwright 未运行；原生路由及页面通过上述设备测试验证。

## 刷新与快速滚动调整

刷新指示器参考本机 Read 的 `PullToSyncIndicator.kt`：下拉时跟随位移、透明度与缩放，
松手后弹回，使用官方 `LoadingIndicator` 播放不定进度变形动画，完成后淡出。

快速滚动采用 LibChecker 应用列表实际使用的
`me.zhanghai.android.fastscroll:library:1.3.0`，调用 `useMd2Style()`。
原生 View overlay 负责滑块、触摸命中与自动隐藏，适配器只向现有 LazyColumn
提供滚动范围和位置，跳转请求合并为最新目标。列表恢复两侧 16dp 留白，
不为滚动条单独占据一列。

参考源码：

- [LibChecker AppListFragment](https://github.com/LibChecker/LibChecker/blob/master/app/src/main/kotlin/com/absinthe/libchecker/domain/app/list/ui/AppListFragment.kt)
- [AndroidFastScroll](https://github.com/zhanghai/AndroidFastScroll)

本轮最终验证：Pixel 8 Pro 的三项 `ExpressiveTrainListTest` 全部通过（25.581 秒），
包含官方动画不同帧及淡出、20,000 条虚拟列表、原生滚动条的中段／首尾／四分之一定位、
拖动不触发刷新、日期切换及详情返回位置恢复。
`:app:assembleDebug`、`:app:assembleDebugAndroidTest`、`:app:lintDebug --offline` 均通过。
通知栏曾遮挡应用导致一轮焦点失败；收起通知栏后未改产品代码，完整重跑通过。
