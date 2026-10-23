# 自动构建与正式发布

## 首次配置 GitHub

先在仓库 Settings → Environments 创建 `release` 环境，再推送版本标签。
添加 Required reviewers，选择自己的 GitHub 账号；关闭 Prevent self-review，
以便批准自己触发的发布。若界面支持，关闭管理员绕过审批。
Deployment branches and tags 选择 Selected branches and tags，仅添加标签规则 `v*`。
公开仓库可使用此审批功能。

必须手动启用 Required reviewers。仅在 YAML 中写 `environment: release`
不会自动创建审批规则；缺少此配置时，工作流可能直接发布。

在该环境的 Secrets 中添加：

| 名称 | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 已有签名密钥库文件的 Base64 内容 |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥密码 |

在该环境的 Variables 中添加 `ANDROID_SIGNING_CERT_SHA256`，
填写原有正式签名证书的 SHA-256 指纹，可含冒号。
发布脚本会验证独立 APK 和 APKS 内每一个 APK 的签名与此指纹一致。
密钥库文件、密码和 Base64 内容不得提交到仓库或写入构建日志。

Windows PowerShell 中将密钥的 Base64 内容复制到剪贴板，然后粘贴进 Secret：

```powershell
$keyPath = 'D:\安全目录\release.jks'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)) | Set-Clipboard
```

填写后清空剪贴板，并留意系统剪贴板历史和同步功能：

```powershell
Set-Clipboard -Value ''
```

用 JDK 的 keytool 查询证书指纹，按提示输入密码：

```powershell
keytool -list -v -keystore 'D:\安全目录\release.jks' -alias '你的别名'
```

确认指纹对应之前分发的正式版，而非调试密钥。密钥需自行离线备份。
仓库需允许 Actions 运行；发布任务通过 `GITHUB_TOKEN` 的 `contents: write`
权限发表 Release，无需额外配置个人访问令牌。

## CI 版本

CI 和发布环境使用 Android Command-line Tools 22.0（构建号 15859902），
平台包为 `platforms;android-37.0`，Build Tools 为 `build-tools;37.0.0`。
平台包名与 `compileSdk` 的整数写法不同，更新 SDK 时应先核对 Google SDK 仓库中的实际包名。

每次分支推送、Pull Request 和手动运行 Android CI 都会执行网页测试、
Android 单元测试、Lint 并构建 CI APK，产物在 Actions 的 Artifacts 中保留 14 天。
只在本地提交而没有推送不会触发构建。
CI 应用 ID 为 `cc.samlab.ticket.ci`，使用调试签名，可与正式版同时安装。
托管 Runner 的调试密钥可能随运行变化，CI 包覆盖安装可能需要先卸载旧 CI 包。
CI 和 PR 不读取正式签名 Secrets。设备测试不包含在此流程内。
两条工作流均运行 debug 变体的共享单元测试，分别对 CI 和 release 变体执行 Lint。

## 发布版本

1. 在 `gradle.properties` 设置 `crticket.version`，并准备 `changelogs/X.Y.Z.md`。
2. 完成提交并推送，确保 CI 通过。
3. 在同一个提交创建并推送版本标签：

```powershell
git tag v0.9.3
git push origin v0.9.3
```

标签须为 `vX.Y.Z`，并与该提交的 `crticket.version` 相同。
对应的 `changelogs/X.Y.Z.md` 必须存在且非空，Release 正文直接使用该文件的全部 Markdown 内容，
不使用 GitHub 自动生成的提交记录。推送标签前先确认版本日志内容已经定稿。
Android Release 先执行测试、Lint，构建未签名 APK、AAB 并保存混淆映射。
构建成功后，进入 Actions 中对应的运行，点击 Review deployments，批准 `release`。
获批后才读取签名密钥，签名 APK，并用固定版本 bundletool 从 AAB 生成 APKS。
签名校验通过后自动创建 Release 草稿、上传附件，最后转为公开发布。

附件为 `CRTicket-vX.Y.Z.apk`、`CRTicket-vX.Y.Z.apks` 和 `SHA256SUMS.txt`。
APK 可以直接安装，APKS 是按设备配置拆分的 APK 集合，可通过 bundletool 或兼容安装器安装。
未签名 AAB 和混淆映射保留在 Actions 的 `unsigned-release` artifact 中 14 天，
如需长期排查混淆堆栈，请及时另行保存 mapping.txt。

如上传失败而留下草稿，先检查并删除该草稿，再重跑失败任务。
已公开的版本不要重复发表或移动标签，应递增版本号。

签名校验兼容旧版 `Signer #1` 和 Build Tools 37 的 `V2 Signer`、`V3 Signer`
证书输出格式。读取不到证书时停止；每个签名证书都必须匹配配置的 SHA-256 指纹。
证书指纹与 APK 文件的 SHA-256、证书公钥 SHA-256 是不同值，不可混用。
指纹不匹配时日志会显示预期值和实际证书值，二者均为公开证书信息，不包含私钥。

工作流和脚本取自发布标签对应的提交。修复主分支后，重跑旧标签任务仍使用旧脚本。
若该版本尚未公开发布，需要确认后将标签改到修复提交并重新触发；
已公开发布的版本应使用新版本号和新标签。

Android versionName 继续采用项目原有的版本号、Git 提交与上游日期组合。
versionCode 根据完整 Git 历史计数，因此 checkout 使用 `fetch-depth: 0`。
正式发布沿同一主分支推进，避免重写历史导致 versionCode 倒退。
构建默认从仓库内 `web/src/views/MainView.vue` 获取上游日期，
仍可通过 Gradle 的 `-Pcrticket.upstreamMainView=...` 显式覆盖来源。

签名步骤结束后会删除 Runner 临时目录中的密钥和密码文件。
保护主分支、版本标签和工作流文件的修改权限，仅批准自己信任的发布提交。

## 工具和 IDE 配置

网页构建使用 Node.js 24，JavaScript Actions 也使用 Node.js 24 运行时。
`node-version` 控制项目构建版本，Action 自身的运行时由所引用的 Action 版本决定。

`.idea` 仅跟踪 `codeStyles` 和 `inspectionProfiles`，其他 IDE 状态和本机 JDK 配置被忽略。
已经跟踪的本地配置需从 Git 索引移除，单独添加忽略规则不会停止跟踪。
本机文件保留，由 Android Studio 继续管理；共享启动配置位于 `.run`，继续纳入版本管理。
