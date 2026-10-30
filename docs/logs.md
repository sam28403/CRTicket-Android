# 本地日志

构建、测试、安装及故障排查产生的日志统一放在 `logs/`，禁止写入项目根目录。
运行命令前确认当前工作目录，并使用指向项目 `logs/` 的路径，避免从 `web/` 等子目录重定向到根目录。

例如，在项目根目录运行：

```powershell
.\gradlew.bat :app:installCi --console=plain > logs/ci-install.log 2>&1
```

整个 `logs/` 目录由 `.gitignore` 排除，不提交到版本控制；`*.log` 规则同时保留，防止散落日志被误提交。日志包含本地路径、设备状态和临时诊断信息，不作为项目源文件维护。
需要保留的结论和验证记录写入 `docs/` 中的相关项目文档。

## 历史日志归档

此前日志主要位于 `build/logs/`（含其 `archive/`）、`build/`、`build/verification/` 和 `state/`，现已迁移到 `logs/archive/`，归档内保留原始相对路径，避免同名覆盖。迁移清单及 SHA-256 校验值保存在本地 `logs/archive/migration-manifest.json`。

本次迁入357份历史日志，加上前次从根目录整理的10份，共367份人工运行和验证日志。npm、Gradle和Android构建工具内部管理的缓存或构建产物日志仍由工具自行管理，不迁移依赖缓存。截图、报告和数据文件不随日志移动。
