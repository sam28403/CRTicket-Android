# 地图源

设置中的“地图选择”和车站大屏“地图”右侧的设置图标进入同一个三级设置页。
顶部预览始终定位北京站；选择立即保存，返回车站大屏或车速表历史轨迹后使用所选来源。
Android 使用本地偏好设置保存；网页使用浏览器本地存储。

## 备份与恢复

地图选择属于“设置项”备份内容。完整备份或勾选“设置项”时会导出并恢复地图源；
未勾选“设置项”时不包含该字段，也不会修改当前地图选择。
备份继续使用 schemaVersion 2，原生 `settings.native.mapSource` 与
网页 `settings.storage.mapSource` 使用同一组稳定来源 ID。
原生导出以 Android 偏好为准，并同步写入可供网页恢复的字段；
只有其中一个字段的备份也可在另一端导入。两者都存在时以原生字段为准。
网页未配置来源时导出“自动”，旧备份完全没有地图字段时保留目标设备当前选择。

不支持的来源 ID 或错误字段类型会在写入前拒绝，避免静默换源。
恢复采用已有备份事务及回滚机制；原生偏好监听和网页备份恢复事件会同步更新界面。

## 来源

- 自动：依次尝试 OSM France HOT、OpenStreetMap 标准、OSM France 标准、OpenTopoMap。
- OSM交通：使用 OSM 官方的 `transportmap` 嵌入页，保留其署名和交通图层。
- OpenRailwayMap：使用 `openrailwaymap.app` 的官方矢量样式与铁路基础设施图层，
  叠加 OpenStreetMap 标准底图，保留双方可见署名。MapLibre GL JS 按需加载；
  支持车站定位和本地历史轨迹，完整轨迹不上传。
  仅显示当前铁路基础设施，移除历史地图和地形数据来源，不额外连接这些可选服务。
  MapLibre v6 的 Vite 接入按官方安装说明，使用 `?worker&url` 打包独立 Worker，
  并在创建地图前调用 `setWorkerUrl`。Worker 随应用本地提供，不依赖外部 CDN。
- OpenStreetMap 标准地图。
- OSM France HOT：保留原有的默认地图样式。
- OSM France 标准地图。
- OpenTopoMap：地形地图。

自动模式只在当前视野加载失败时尝试下一源，不并行下载全部来源；全部失败才警告。
手动来源失败时不切换到其他来源，显示警告并要求用户换源。
栅格来源通过 Leaflet 瓦片错误和十秒超时判断失败。切换来源或离开页面会清理旧监听和定时器。
交通图层是跨域嵌入，网页只能检测嵌入页与服务连通性，不能直接读取内部瓦片状态；
Android 额外监听该独立 WebView 中交通地图资源的 HTTP 和网络错误。
地图 WebView 不暴露原生桥接；交通资源域名仅在该地图选择时放行。

铁路矢量来源通过隔离地图 WebView 的 `/railway-map/{来源}/` 读取公开资源。
Android 仅将所选来源的路径映射到固定服务域名，不转发 Cookie、账户凭据或完整轨迹，
不跟随重定向，不允许获取脚本或 HTML，单资源上限 8 MiB，超时 20 秒。
此适配处理 OpenRailwayMap 样式缺少跨域响应头的情况；WebView 继续禁止混合内容。
Vite 开发预览提供相同固定来源代理；单独部署网页版时，服务器也需配置该路径的代理。
自动模式仍只轮换原有栅格来源，不自动切入铁路矢量来源。

2026-10-30 在 Pixel 8 Pro 的 CI 包上验证 OpenRailwayMap 北京站铁路图层、线路与站名已显示。
其官方代理配置会拒绝没有 Referer 的瓦片和 TileJSON 请求；原生资源转发使用真实的
`https://appassets.androidplatform.net/` 页面来源和 CRTicket 应用 User-Agent。

已移除中国铁路地图及其专用代理、网络明文例外和致谢项。
旧设置或备份中的 `cnrail` 兼容为“自动”，不再连接该服务。

## 服务使用

这些公共服务不保证可用性，使用时必须保留可见署名，并遵守各提供方的使用条件。
不提供离线下载、批量抓取或预加载功能。Android 地图 WebView 的 User-Agent 包含 CRTicket。

- [OpenStreetMap 瓦片政策](https://operations.osmfoundation.org/policies/tiles/)
- [OSM France 地图及使用条件](https://www.openstreetmap.fr/fonds-de-carte/)
- [OpenTopoMap](https://opentopomap.org/about)
- [OpenRailwayMap 使用政策](https://github.com/hiddewie/OpenRailwayMap-vector/blob/master/USAGE.md)

公共服务的“免费”不代表无限流量或服务保障；正式发布前应再次核对服务条款。

## 历史轨迹

车速表历史与车站大屏复用同一地图源偏好和隔离的地图 WebView。
轨迹分段、折线与起终点标记在本地绘制，不向地图服务上传完整轨迹或记录。
在线底图请求会向所选服务提供当前视野所需的瓦片坐标；OSM交通官方嵌入页接收视野 bbox。
交通底图位于本地折线下方，视野变化后同步更新嵌入页，保留本地地图的缩放和拖动操作。

## 轨迹速度配色

历史记录保存位置和毫秒时间戳；地图根据相邻定位点的大圆距离与时间差估算分段平均速度。
0 至 400 km/h 按蓝、亮绿、黄、红线性插值，超出范围的速度使用端点颜色。
不使用深绿色和紫色。无有效时间戳或时间倒退时使用中性灰色，不编造速度。
暂停分段和无效坐标处不会跨段连线；信号中断前后的线段仍是记录中已有的直线补连。
折线使用亮色描边与 Canvas 渲染，相同颜色共用绘制图层，减少长轨迹的对象数量。
