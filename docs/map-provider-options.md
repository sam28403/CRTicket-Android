# 其他地图服务的免密钥接入

本次核查以服务方公开文档为依据。免费额度与免账号、免密钥是不同条件。

| 方案 | 账号或密钥要求 | 对当前地图的适用性 |
| --- | --- | --- |
| Google Maps JavaScript / Maps Embed API | 正式 API 需要开发者申请 Key；Embed API 免费，但仍要求 Cloud Key 和计费账号 | 不符合完全免账号、免 Key 的接入条件 |
| Google Maps 分享嵌入 | 从 Google Maps 分享界面复制嵌入 HTML | 可展示分享地图，不能等同于用于动态轨迹叠加的可编程地图 API |
| Google Maps URLs | 官方明确不需要 API Key | 适合外部打开地点或路线，不是应用内的轨迹底图 |
| 高德 JS API 2.0 | 需要开发者注册并申请 Key，新 Key 还需要安全密钥 | 不符合完全免账号、免 Key 的接入条件 |
| OpenFreeMap | 官方说明不需要注册或 API Key | 符合免 Key 要求，但提供矢量地图，需要增加 MapLibre 等矢量渲染支持；本次未加入设置 |

当前车站地图和历史轨迹继续使用已有的免费公共来源。
不将外部跳转链接混入应用内底图来源列表。

资料：

- [Google Maps Embed API](https://developers.google.com/maps/documentation/embed/quickstart)
- [Google Maps 分享与嵌入](https://support.google.com/maps/answer/7101463?hl=en)
- [Google Maps URLs](https://developers.google.com/maps/documentation/urls/get-started)
- [高德 JS API 准备工作](https://lbs.amap.com/api/javascript-api-v2/prerequisites)
- [OpenFreeMap](https://openfreemap.org/)
- [OpenFreeMap 矢量接入示例](https://openfreemap.org/quick_start/)
