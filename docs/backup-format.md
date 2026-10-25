# 备份安全与格式兼容

## 使用

导出时先选择备份内容，再设置备份密码。默认开启加密；用户主动关闭后才导出旧版明文 JSON。
加密密码至少 8 个字符，建议使用较长且不重复的密码。再次输入用于确认密码。
导入加密文件时，先解密并校验，再选择恢复内容；密码错误或认证失败时不会恢复任何数据。
首次启动的导入入口和设置中的导入入口共用此流程。

原生解密成功回调单独传递原始 JSON，允许达到明文备份上限，
不将大文件嵌入普通设置消息的 500KB JSON 封装，避免回调被丢弃后界面无限等待。
解锁等待超过 60 秒会退出等待并提示失败；迟到回调不再处理，可以重新输入密码重试。

恢复前按目标设备的实际权限过滤功能开关。到站检测缺少定位、后台定位或通知等必要条件时保持关闭，
起售闹钟模式缺少精确闹钟、全屏提醒、勿扰访问或通知权限时保持关闭。
这些开关不会因为事后授予权限而自动开启；用户需自行开启。
权限不足不会阻止恢复车票、个人资料、搜索历史、到访记录或历史轨迹。

跨设备恢复只需要完整备份文件和对应密码，不需要旧设备、设备 ID、账号、系统密钥或原安装目录。
密码不保存到设置、备份头、SavedState 或服务端。忘记密码无法解密；请在独立的密码管理器中妥善保存。
不要把密码和备份文件一起公开分享。明文导出及 Excel、图片、PDF 导出均不受此加密保护。
加密保护导出的文件，不能防止已解锁设备上的屏幕查看或恶意软件读取运行中应用的数据。

明文有效载荷上限为 20 MiB；为容纳 Base64 与格式头，导入及保存文件上限为 28 MiB。
文件扩展名仍为 `.json`，加密文件的外层不包含用户资料、车票、轨迹或导出时间。
浏览器使用 Web Crypto，需要 HTTPS 或受信任的 localhost 安全环境；不支持时直接报错，不降级为明文。

## 不随应用版本变化的格式

加密封装版本与应用版本、版本数值、内部有效载荷的 schemaVersion 完全分离。
读取时禁止与 BuildConfig.VERSION_NAME / VERSION_CODE 比较，也不依赖当前的默认加密参数。

外层加密封装 v1：

```json
{
  "format": "CRTicketEncryptedBackup",
  "version": 1,
  "kdf": {
    "name": "PBKDF2-HMAC-SHA256",
    "iterations": 600000,
    "salt": "Base64 of 16 random bytes"
  },
  "cipher": {
    "name": "AES-256-GCM",
    "iv": "Base64 of 12 random bytes"
  },
  "data": "Base64 of ciphertext followed by the 16-byte authentication tag"
}
```

v1 的约定不可因升级而改变：

- 密码直接按 UTF-8 编码，不去除空格，不做 Unicode 归一化。
- 派生 256 位 AES 密钥；每次导出用密码学随机源重新生成 salt 和 iv。
- AES-GCM 使用 128 位认证标签；附加认证数据是 UTF-8 字符串 `CRTicketEncryptedBackup:1`。
- Base64 使用标准字母表和填充；密文末尾附加认证标签，与 Web Crypto 的返回格式一致。
- v1 读取器接受文件声明的整数迭代数 100000 至 2000000，禁止不受限制的派生工作量。
- 未知封装版本或算法必须报错，禁止尝试明文导入或自动改用其他算法。
- 解密认证成功后才解析内部备份，仍执行原有字段校验、内容选择、重复合并和回滚。
- 校验内部有效载荷后，用户仍需明确确认恢复；解密本身不修改本机数据。

内部有效载荷沿用既有格式：旧版车票数组、schemaVersion 1 车票备份、schemaVersion 2 完整／部分备份。
新版本必须保留这些明文读取路径与封装 v1 读取器。
以后新增封装 v2 时增加独立读取器；以后新增数据 schema 时增加从旧 schema 的明确迁移。
不得重用已有版本号表达新含义，也不得通过重新生成固定测试样本来消除兼容性失败。
旧应用不一定能读取新加密格式；本兼容约定保证的是新应用继续恢复历史备份。

## 验证与固定样本

`app/src/androidTest/assets/backup-encryption-v1.json` 是独立 Node crypto 实现生成的永久兼容样本。
其盐与 iv 固定仅用于测试，生产导出始终随机生成。样本中的密码和虚构个人资料是公开测试数据。

`web/src/android/backupEncryption.test.js` 使用此固定样本验证 v1，
并用独立 Node PBKDF2 / AES-GCM 实现解密新导出，避免只有同一实现的往返测试。
同时验证中文与组合字符密码、首尾空格、随机性、错误密码、被篡改的密文／salt／iv、
不支持的版本、异常派生参数和全部历史明文格式。

`EncryptedBackupDeviceTest` 验证认证失败时网页存储与原生设置均不变化，
正确密码恢复资料和查询记录，默认加密导出可再次恢复。
测试还可从应用外部文件目录读取 `cross-device-backup.json`，
用于将一台设备实际生成的 `portable-backup.json` 复制到另一台设备后验证真实跨设备恢复。

大文件回归使用独立 JCA 实现加密包含 8000 条到访记录的有效备份，
验证超过 500KB 的解密结果能返回原生内容选择界面，并在排除到访记录后正常恢复其他类别。
`BackupPermissionDeviceTest` 在拒绝权限的上下文中验证恢复与完成阶段开关均为关闭、
取消权限提示不会开启功能，且无关设置与搜索历史正常恢复。

实现依据：[OWASP 加密存储建议](https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html)、
[Web Crypto 密钥派生接口](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto/deriveKey)。
