# 开源致谢

关于页面的“开源致谢”位于“报告错误”右侧，打开 `/about/credits` 三级页面。
页面使用自适应卡片网格展示项目名称和链接，点击整张卡片调用现有外部浏览器入口。
手机和大屏均保留关于页面的导航归属及返回栈。

链接清单维护在 `ProjectCredits.kt`，依据 Gradle、网页依赖、实际数据接口及素材许可整理。
网站与数据来源不代表开源软件，也不代表项目获得其官方背书。
此页面提供来源入口，已有许可文件继续保存在 `web/public/licenses`。
按钮和页面标题的语言资源维护在各语言的 `strings.xml`。

小测验的全对礼花使用`nl.dionsegijn:konfetti-compose:2.2.0`，与Android Easter Eggs
项目采用同一动画库。项目来源同时加入致谢，Konfetti的ISC许可保存于
`web/public/licenses/konfetti.txt`并随网页资产打包。本项目使用独立的粒子配置，
未复制Android Easter Eggs的动画控制器或主题混色实现。
