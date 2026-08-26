# 第三阶段验证报告

## 版本

- versionName：`1.8.0`
- versionCode：`87`
- 阶段范围：流式输出刷新与多附件内存、取消、错误处理

## 本阶段改动

- 流式 UI 改为只向 WebView 发送尚未显示的增量，避免每个片段重复传输完整回答。
- 流式完成时仍使用完整 Markdown 内容做最终代码、公式和附件渲染。
- 普通文件采用流式 Base64 编码，避免同时保留原始字节数组和完整 Base64 副本。
- 图片先按上传尺寸缩放并压缩，再进入请求；界面显示压缩后的大小。
- 增加普通内联附件的总请求预算，超过预算时在发送前拦截。
- Office 文件使用本地临时文件和轻量文本提取，不进入 Base64 内联预算。
- 多附件逐个显示准备状态；读取过程中支持停止请求。
- 附件读取失败显示具体文件名，并处理附件阶段的内存不足异常。
- 停止或失败后保留输入和附件；重试相同请求时复用原用户消息，避免重复气泡。
- 重答或修改要求失败时保留原分支父节点。

## 自动化验证

在仓库根目录执行并通过：

```powershell
$env:ANDROID_HOME=(Resolve-Path ".tools\android-sdk").Path
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
& ".tools\gradle-8.10.2\bin\gradle.bat" clean :app:testDebugUnitTest --no-daemon --console=plain
& ".tools\gradle-8.10.2\bin\gradle.bat" :app:assembleDebug :app:lintDebug --no-daemon --console=plain
node scripts/test-chat-math-normalization.js
```

验证结果：

- `BUILD SUCCESSFUL`
- 单元测试通过，包含流式增量缓冲、附件摘要、重复/超限、总内联预算和批量删除规则。
- Debug APK 构建通过。
- Android Lint 通过。
- 数学文本规范化脚本输出 `chat math normalization ok`。
- `adb devices` 未发现已连接的 Android 真机，因此未完成真机安装和运行验证。

## APK 校验

- 构建文件：`app/build/outputs/apk/debug/app-debug.apk`
- 交付副本：`03_Output/CodexMobile-1.8.0-debug.apk`
- 包名：`com.codex.apikeychat`
- SHA-256：`69089B57C4CCFEE01291F5DB0B8647571382831A292AF49B6DA00BE01F025B3B`

## 安全检查

- 未把 API Key、Bearer token、签名文件或本地配置加入交付文件。
- APK 和本地工具链由 `.gitignore` 忽略，不应提交到 Git 仓库。

## 真机测试范围

1. 发送一条普通短消息，确认流式文字连续显示且没有重复片段。
2. 发送较长回答，确认后半段仍持续显示，完成后代码块和公式正常渲染。
3. 连续选择 2-6 个图片/普通文件，确认附件逐个显示“准备中/已准备”状态。
4. 组合多个较大普通文件，确认发送前提示附件组合过大，输入和附件仍保留。
5. 发送大图片，确认应用先压缩后发送，不闪退并显示压缩后大小。
6. 选择 Office 文件，确认仍能读取并且不会被错误地当作 Base64 文件上传。
7. 附件准备或请求进行中点击停止，确认输入框和附件恢复，当前页面不变空白。
8. 停止后再次发送完全相同的内容和附件，确认不会出现重复用户气泡。
9. 对已有回复执行重答或修改要求后停止，确认原分支仍可切换，重试不会破坏分支关系。
10. 重复打开历史记录和带图片的会话，确认页面不会因流式残留节点卡死或空白。
