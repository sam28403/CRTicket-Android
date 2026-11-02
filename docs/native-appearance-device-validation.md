# 原生设置与独立页面主题实机验证

验证日期：2026-11-02。

使用独立应用 `cc.samlab.ticket.ci` 和测试包 `cc.samlab.ticket.ci.test`，未覆盖正式应用的数据。

## 设备与最终结果

| 设备 | 系统 | NativeAppearanceDeviceTest |
| --- | --- | --- |
| Pixel 8 Pro | Android 17 | OK (2 tests)，25.141 秒 |
| Pixel Tablet | Android 17 | OK (2 tests)，25.421 秒 |

最终输出位于 `logs/native-appearance-0-complete.log` 和 `logs/native-appearance-1-complete.log`。

## 覆盖范围

- 欢迎页、更新日志、独立进程错误报告页分别验证手动浅色、暗色和纯黑模式；保存截图并检查页面像素的明暗比例。
- 模拟网页设置请求始终不返回，通过 Compose 界面修改显示名称、选择暗色和打开纯黑模式，再调用原生到站检测关闭操作；确认原生状态、持久化资料及网页资料同步正确，原网页请求仍保持未完成。
- 错误报告页通过与实际异常处理相同的 Intent 工厂启动；本轮未故意触发应用崩溃或无响应。
- 本轮未覆盖头像裁切、到站检测开启授权流程及实际地理围栏通知，不将这些流程计入实机通过范围。

## 本轮发现与修复

独立诊断进程复用时会缓存旧 SharedPreferences，导致主进程切回浅色后，错误页继续使用暗色。异常报告启动时现传递主题偏好，由错误页结合当前系统配置解析，避免读取该进程缓存的旧偏好。两台设备均重现过修复前的问题，最终回归通过。

早期测试还遇到跨进程页面无法由 startActivitySync 启动、主题摘要与单选项同名、锁屏与 Activity 焦点时序问题。最终测试使用系统前台 Activity 信息校验诊断页，读取并等待返回按键命令完成，确认诊断页退出后再切换页面，并在启动前唤醒屏幕。失败轮次的日志保留，不以失败轮次声称通过。

## 产物

截图通过 `Context.getExternalFilesDir()` 保存到各设备 CI 应用专属目录 `native-appearance-tests/`，本地副本位于 `logs/native-appearance-phone-complete/` 和 `logs/native-appearance-tablet-complete/`。平板截图中可能包含用户已有的画中画窗口。

CI 构建和 CI 单元测试通过，最终 Debug APK 构建和 Debug 单元测试也通过。构建日志分别为 `logs/native-appearance-cross-process-build.log` 和 `logs/native-appearance-debug-final.log`。
