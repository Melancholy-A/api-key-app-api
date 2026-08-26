# 附件功能 1.7.6 验证报告

## 本次改动

- 附件面板增加独立标题栏，可展开或收起附件列表；收起时仍保留附件并可继续发送、添加和清空。
- 选择附件后自动展开列表，清空或发送成功后重置折叠和选择状态。
- 增加附件数量、图片/文件数量、已知总大小的发送前检查。
- 普通附件限制为 20 MB，Office 附件限制为 120 MB；超限时保留当前输入和附件，并提示具体文件。
- 拒绝重复选择同一个 URI 附件。
- 附件标题栏增加紧凑批量管理入口；可进入选择模式，点选多个附件后用垃圾桶一次删除。
- 保留原有单项删除、复制文件名、清空全部和长按附件菜单。
- 版本号更新为 `1.7.6`，versionCode 更新为 `85`。

## 修改文件

- `app/src/main/java/com/codex/apikeychat/AttachmentRules.java`
- `app/src/main/java/com/codex/apikeychat/MainActivity.java`
- `app/src/test/java/com/codex/apikeychat/AttachmentRulesTest.java`
- `app/build.gradle`

## 自动化验证

在仓库根目录执行，均成功：

```text
gradle clean :app:testDebugUnitTest --no-daemon --console=plain
gradle :app:assembleDebug :app:lintDebug --no-daemon --console=plain
```

结果：

- 单元测试任务通过，包含附件摘要、重复/超限校验、Office 限额和批量删除规则。
- Debug APK 构建通过。
- Android Lint 通过。
- `git diff --check` 无代码空白错误。
- 追踪文件未发现 API key、Bearer token 或常见平台密钥样式字符串。

## APK

- 本地构建文件：`app/build/outputs/apk/debug/app-debug.apk`
- 交付副本：`03_Output/CodexMobile-1.7.6-debug.apk`
- 包名：`com.codex.apikeychat`
- versionName：`1.7.6`
- versionCode：`85`
- SHA-256：`F9A74972B6DE48603F2B41C89F4FCBEA1925F31ED3B115C8B1D80488867EED83`

## 手机手动测试清单

1. 连续选择 2-3 个图片/文件，确认附件区显示数量、类型标签和文件大小。
2. 点附件标题栏的收起图标，确认列表隐藏但标题栏保留；再次点击确认列表柔和展开。
3. 收起后继续添加附件，确认列表自动展开且旧附件仍在。
4. 选择同一个文件两次，确认第二次不会重复加入。
5. 点批量管理图标，点选多个附件，确认选中数量变化；点垃圾桶，确认只删除选中的附件。
6. 长按单个附件，确认原有紧凑菜单仍可复制文件名、删除单项和清空全部。
7. 发送前观察状态栏摘要，确认显示附件数量、图片/文件数量和总大小。
8. 使用超过限制的普通文件或模拟超限条目，确认发送被拦截，输入和附件不丢失。
9. 正常发送带多个附件的消息，确认请求完成后附件区自动清空。
10. 请求中点击停止，确认附件和输入状态按原有停止流程工作，不出现空白对话。

## 说明

本报告不包含 API key、聊天内容或个人隐私。当前构建是 Debug APK；正式公开发布前仍建议使用正式签名配置构建 Release APK，并在真机上完成第 1-10 项手动测试。
