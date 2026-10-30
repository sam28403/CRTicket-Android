# 车站大屏紧凑布局与网格验证

日期：2026-10-17。

## 布局

- 手机缩小标题栏及查询区间距，趟数、定位、跟随放在同一行；四个标签保留。
  大字体下操作区允许换行，避免压缩触摸目标。
- 可用宽度达到 600dp 时，标题和查询区并排；达到 720dp 时，标签和操作区并排。
  阈值使用内容分区的实际宽度，不依赖设备型号。
- 三种车次视图改用 LazyVerticalGrid。卡片最小宽度 320dp，间距 8dp，
  左右各留 16dp；宽度足够时自动增加为两列、三列或更多列。
  按行从左至右保持原来的车次顺序，没有一次性创建全部卡片。
- 每个车站、每个标签持有独立 LazyGridState，定位与跟随仍按车次索引操作。
  返回同一车站继续保留标签、跟随状态和滚动位置。
- 全部信息的车次继续使用 google_sans_rounded_regular.ttf。

## 实机测量

以下高度为应用内容区，排除系统状态栏与导航栏。

| 设备/分区 | 内容区尺寸 | 顶部控件高度 | 顶部占比 | 卡片列数 |
| --- | --- | ---: | ---: | ---: |
| Pixel 8 Pro 竖屏 | 395.3 × 811.5dp | 204.4dp | 25.2% | 1 |
| Pixel Tablet 横屏详情分区 | 841.5 × 732dp | 112dp | 15.3% | 2 |
| 平板模拟竖向折叠详情分区 | 632 × 732dp | 156dp | 21.3% | 1 |
| 平板模拟横向折叠详情分区 | 1280 × 360dp | 112dp | 31.1% | 3 |

普通模式下，全部信息、出发大屏、到达大屏均检查了列数、卡片边界、
顶部占比、控件可见性，并保存截图。模拟折叠使用 WindowLayoutInfoPublisherRule，
检查网格位于详情分区且没有跨越折痕；不代表真实折叠屏硬件验证。

## 功能回归

两台设备均完成：

- 真实北京南查询、四个标签、定位与跟随；地图线条和瓦片加载。
- 地图切回全部信息后，点击完整可见的车次文字进入对应路路通车次页面。
- 卡片、车次、站名的点击跳转，以及返回车站后的标签和精确滚动位置恢复。
- 车站返回显示采样：手机 75 帧、平板 39 帧；最低 alpha 均为 1，隐藏帧均为 0。
- 无效查询及空结果重试。

网格会预加载裁切区域的节点；点击回归已改为选择完整可见卡片和车次文字，
不再依赖单列列表的“第二个节点”或包含数字的通用文字匹配。

构建及检查：assembleDebug、assembleDebugAndroidTest、assembleBenchmark、
testDebugUnitTest（61 项）、lintDebug 均通过。功能检查在 debug APK 完成，
最后将非调试 benchmark 优化包装回两台设备。

日志：

- `logs/archive/build/station-adaptive-final-build.log`
- `logs/archive/build/station-adaptive-phone-final.log`、`logs/archive/build/station-adaptive-tablet-final.log`
- `logs/archive/build/station-map-navigation-phone.log`、`logs/archive/build/station-map-navigation-tablet.log`
- `logs/archive/build/station-adaptive-optimized-build.log`

最后两个地图跳转日志为修正可见节点定位后的通过结果，其余回归与布局数据
记录在前面的两台设备日志中。截图位于 `build/verification/station-adaptive/`。
