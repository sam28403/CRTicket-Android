# 旧电脑后端归档

`desktop-server` 保存电脑后端参考代码，不参与 APK 构建。本地数据库与运行数据不纳入版本管理。

手机独立版的运行逻辑位于：

- `app/src/main/java/cc/samlab/ticket/network`：手机网络请求和上游地址限制。
- `web/src/android/railway`：12306 查询、路路通解析、手机缓存和列车列表。
- `web/src/android/localApi.js`：手机车票数据和默认用户。
