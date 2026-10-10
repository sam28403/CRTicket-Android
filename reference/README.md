# 旧电脑后端归档

`desktop-server` 保存前一版电脑后端代码及原有数据库，供需要时迁移历史数据。该目录不参与 APK 构建，服务已停止。

手机独立版的运行逻辑位于：

- `app/src/main/java/cc/samlab/ticket/network`：手机网络请求和上游地址限制。
- `web/src/android/railway`：12306 查询、路路通解析、手机缓存和列车列表。
- `web/src/android/localApi.js`：手机车票数据和默认用户。
