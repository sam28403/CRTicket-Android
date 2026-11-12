# 中老铁路时刻表

老挝段在线车次使用 [Laos Bus Tickets 时刻表](https://laosbustickets.com/train-tickets-laos/#timetable)，源数据为 [JSON](https://laosbustickets.com/data/laos-train-timetable.json)，按 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) 使用。中文站名参考[国家发展改革委公布的老挝段客运站](https://www.ndrc.gov.cn/fggz/lywzjw/202206/t20220629_1374240.html)。

简体中文文案使用「中老铁路」「万象时间」，英文使用「Laos-China Railway」「Vientiane time」，繁体中文使用「中寮鐵路」「永珍時間」。站名沿用时刻表的站名数据。

- 在线数据按 `runsOn` 匹配始发日期，仅接入老挝境内的非 D 字头车次。按车次号查询 C85、C86 时，优先显示 12306 的昆明至香格里拉班次。存在同号线路时，可以在详情中切换。
- 从列表、搜索或车站大屏点开老挝段车次时，优先查询所选始发日期的在线时刻。在线时刻不可用时，显示同一车次和日期的离线快照。主动选择离线查询、关闭联网或日期超出在线范围时，直接读取离线时刻。
- 在线缓存随应用查询会话保留。万象时间每天 06:05 后的首次查询检查更新，并合并并发下载。上游未更新或下载失败时，15 分钟后可重试。仍在缓存日期范围内的班次可继续查询。
- 老挝段时刻默认显示万象时间（GMT+7），可切换为北京时间（GMT+8）。D83 至 D88、Y25 至 Y28 默认按停靠站显示当地时间，也可统一显示北京时间或万象时间。车次时刻 PDF 使用当前所选时区及图定时刻。
- 老挝段里程来自 D88 经由里程表。从万象起算，蓬洪、万荣、嘎西、琅勃拉邦、孟阿、孟赛、纳磨、纳堆、磨丁分别为 64、123、167、239、293、339、378、394、406 公里。详情中的累计里程为各站与本次始发站的里程差绝对值。

## 离线快照

车次列表、离线车次详情和老挝段车站大屏使用 [独立快照](../web/src/android/railway/lcr-offline.json)。快照保存北京时间、跨日数及累计里程。车站大屏按万象日期筛选、显示和定位到发车次。现有 12306 大屏正晚点查询以北京时间为准，老挝站大屏使用图定时刻。

车站卡片与大屏共用路路通和离线快照的合并规则，按车次、始发日期和起讫站去重。卡片按车站当地日期统计开行车次，并按当地时间计算已办理和正在停靠的车次。使用导入的路路通时刻表时，卡片与大屏均使用导入数据。

- C85、C86、C91、C92 每日开行。C95 周五至次周周二开行。C96 周四至次周周一开行。这六趟车次有效期为 2026-11-01 至 2027-01-15。
- K11、K12 的有效期为 2026-11-01 至 2026-11-24，周三至周六开行。
- 有效期包含首尾两天。D 字头继续使用 12306 或路路通数据。
- 车次详情的开行日历按同一份规则显示。有效期内按星期标注开行或停运，有效期外不标记。离线查询选到停运日时仍可查看日历。

先更新 [开行规则](../data/lcr-offline-rules.json)，再在项目根目录运行 `node tools/update-lcr-offline.mjs`。也可以把已保存的源文件作为参数传入，例如 `node tools/update-lcr-offline.mjs timetable.json`。脚本会核对每个开行星期是否都有明确的时刻，成功后替换快照。重新构建 Web 资源后，Android 应用会使用新快照。

## 真机回归

- `LcrTrainQueryDeviceTest` 从原生车次列表点开 C85，验证详情显示测试在线响应中的 18:43。随后模拟在线请求失败，从列表点开 C86，验证详情自动显示离线快照中的 15:15。用例同时检查两次查询均保留老挝段线路，并验证「开源致谢」中的 Laos Bus Tickets 卡片打开时刻表链接。
- `LcrStationCardDeviceTest` 使用 2026-11-12 的固定数据，核对万象站搜索卡片与车站大屏都显示 11 趟。两项测试使用受控数据，避免结果随上游服务状态变化。

2026-11-12，Pixel 8 Pro 和 Pixel Tablet 均通过上述 3 项检查。平板的万象站用例在合跑时等待搜索结果超时，单独重跑通过。

在项目根目录构建 CI 应用和测试 APK，分别安装到手机和平板。将 `<设备序列号>` 替换为 `adb devices -l` 中的目标设备序列号。两组测试分别运行，以便单独核对车次详情和车站统计。

```powershell
.\gradlew.bat :app:assembleCi :app:assembleCiAndroidTest '-Pcrticket.testBuildType=ci' > logs/lcr-device-build.log 2>&1
adb -s '<设备序列号>' install -r -t app/build/outputs/apk/ci/app-ci.apk > logs/lcr-device-install.log 2>&1
adb -s '<设备序列号>' install -r -t app/build/outputs/apk/androidTest/ci/app-ci-androidTest.apk >> logs/lcr-device-install.log 2>&1
$runner = 'cc.samlab.ticket.ci.test/androidx.test.runner.AndroidJUnitRunner'
adb -s '<设备序列号>' shell am instrument -w -r -e class cc.samlab.ticket.LcrTrainQueryDeviceTest $runner > logs/lcr-device-train-query.log 2>&1
adb -s '<设备序列号>' shell am instrument -w -r -e class cc.samlab.ticket.LcrStationCardDeviceTest $runner > logs/lcr-device-station-card.log 2>&1
```

测试截图保存在 CI 应用专属的 `Android/data/cc.samlab.ticket.ci/files/tablet-native-pages/` 目录。
