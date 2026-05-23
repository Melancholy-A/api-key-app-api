# ApiKey Chat

[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

一个面向 Android / HarmonyOS 的自带 API Key 聊天应用，同时提供 iOS SwiftUI 源码工程。它可以连接 OpenAI 或兼容 OpenAI 协议的第三方接口，在手机端完成聊天、文件/图片输入、联网搜索、历史记录、图片生成和应用内更新。

[下载最新版 APK](https://github.com/Melancholy-A/api-key-app-api/releases/latest/download/CodexMobile-debug.apk) · [查看 Releases](https://github.com/Melancholy-A/api-key-app-api/releases) · [安全说明](./SECURITY.md)

## 功能特性

- 自带 API Key：密钥保存在本机，Android 使用 Android Keystore 加密，iOS 使用 Keychain。
- 多接口模式：支持 Responses API 和 Chat Completions，可配置兼容接口 Base URL。
- 模型管理：支持刷新 `/v1/models`，也可以手动选择模型。
- 推理质量：支持低 / 中 / 高 / 超高四档 reasoning effort，在速度和复杂推理质量之间切换。
- 流式输出：Responses API 和 Chat Completions 都支持 SSE 增量显示，停止请求响应更快。
- 上下文聊天：本地 SQLite 保存历史会话，打开历史后可以继续追问，旧 JSON 历史会自动迁移。
- 多模态输入：支持图片附件、文件附件和本地 Markdown / KaTeX 渲染。
- 拍照上传：可直接调用相机，拍完后裁剪、旋转、保存到相册，并作为图片附件发送。
- 自动工具模式：Responses API 下可按需调用 App 侧 `custom_search`、`open_url`、`generate_image` 和轻量文档工具；执行时会显示搜索/读网页/生图/导出状态。
- 联网搜索：支持博查 Bocha、Tavily、Brave Search、自定义接口和本地 DuckDuckGo Lite / Bing 兜底；普通搜索先取摘要，深度搜索才打开少数网页。
- 轻量 Office 工具：智能体可生成 `.docx`、`.xlsx`、`.pptx`，也可按需导出 CSV / HTML；Office 文本读取走本地轻量解析。
- 图片生成：支持 `/v1/images/generations`，可识别“生成图片/画一张/设计海报”等明确表达自动走生图流程，生成结果会保存为本地文件引用，避免历史里塞入大段 base64。
- 类 ChatGPT 移动端体验：消息复制、朗读、分享、重新生成、停止请求、编辑用户消息、每条消息补充修改要求、代码块复制和滚动到底部按钮。
- 移动端适配：支持手机/平板、横竖屏切换、底部工具栏收起、应用内网页查看器。
- 自动更新：从 GitHub Releases 检查新版 APK，下载时显示进度，并唤起系统安装器。
- 推理显示：如果接口明确返回 `reasoning_content`、`reasoning.summary`、工具摘要或 `<think>...</think>`，会显示可折叠思考区域。

## 快速安装

1. 打开 [Releases](https://github.com/Melancholy-A/api-key-app-api/releases)，下载最新版 `CodexMobile-debug.apk`。
2. 把 APK 传到 Android / HarmonyOS 手机。
3. 在文件管理器里点开安装。
4. 如果系统拦截安装，按提示给文件管理器或本应用开启“安装未知应用”权限。
5. 打开 App，填写 API Key 和 Base URL，刷新模型后开始聊天。

> HarmonyOS 4.2 可按 Android APK 方式安装。不同机型可能会受到纯净模式、增强防护或企业策略影响。

## 第三方接口配置

Base URL 填接口基础地址，例如：

```text
https://example.com/v1
```

不要填写完整的 `/chat/completions`、`/responses` 或 `/models` 路径，App 会自动拼接接口路径，也会自动剥掉这些常见尾巴。

常见配置建议：

- 如果接口支持 `/v1/responses`，优先使用 Responses API。
- 如果接口不支持 `/v1/responses`，在设置里切换到 Chat Completions。
- 如果直连 OpenAI 返回 `Country, region, or territory not supported`，说明当前网络位置不在 OpenAI API 支持范围内。App 不会绕过这个限制，请使用自己有权访问的合规后端或兼容接口。
- 如果出现 `TLSV1_ALERT_INTERNAL_ERROR`，通常是第三方网关的证书链、SNI、TLS 版本或域名配置问题，不是模型内容问题。

## 联网搜索

推荐使用 `Responses API + 自动工具模式`。模型会根据问题自行判断是否需要搜索。

搜索路线：

- 普通搜索：模型调用 App 侧 `custom_search`，默认推荐博查 Bocha，先返回摘要来源给模型回答。
- 深度搜索：先搜索更多摘要来源，再由模型筛选少数关键网页调用 `open_url` 读取正文片段后综合。
- 缓存：同一搜索服务商、同一 query、同一模式 20 分钟内复用缓存，避免重复扣搜索 API 次数。
- 诊断：工具摘要会显示搜索源、耗时、是否缓存和来源数量；读网页会显示读取耗时。
- 兜底：未配置专用搜索 Key 或服务商失败时，App 最后才尝试内置 DuckDuckGo Lite / Bing。

搜索服务商：

- 博查 Bocha（推荐）
- Tavily
- Brave Search
- 自定义接口
- 本地 DuckDuckGo Lite / Bing 兜底
- 关闭应用侧搜索

自定义搜索 API 支持 GET 和 POST：
  - URL 中包含 `{query}` 或已有查询参数时走 GET。
  - 其他情况走 POST JSON。
- POST 请求体会包含 `query`、`q`、`count`、`num`、`max_results`。
- 鉴权方式支持不鉴权、`Authorization: Bearer`、`X-API-Key` 或 `api_key` 参数。
- 返回结果会解析 `results`、`data`、`items`、`organic`、`webPages.value` 等常见字段。

搜索结果来源可在回复下方直接打开，App 会使用内置网页查看器。

## 表格和演示稿

自动工具模式下，模型可以根据用户要求生成：

- Excel 表格：默认保存为轻量 `.xlsx`，如果文件名写成 `.csv` 则导出 CSV。
- PowerPoint 演示稿：默认保存为轻量 `.pptx`，如果文件名写成 `.html` 则导出 HTML 幻灯片。
- Word 文档：默认保存为轻量 `.docx`。

App 没有引入大型桌面 Office 库，生成和读取都优先使用轻量 OpenXML。这样更适合手机端，能降低生成 PPT / Excel 和读取大 PPT 时的内存崩溃风险；复杂图表、动画、SmartArt 等高级对象暂不保证完整读取。

## 图片生成

图片生成走 `/v1/images/generations`。如果第三方接口对模型名称有限制，可以在设置中把生图模型改为它支持的模型，例如 `image-2`。

生成的图片会保存到 App 本地文件，并进入本地图库；历史聊天中也能继续查看。

## 项目结构

```text
.
├── app/                         Android 工程
│   └── src/main/
│       ├── java/com/codex/...   Java 业务代码
│       ├── assets/chat.html     聊天消息 WebView 渲染层
│       └── res/                 Android 资源
├── ios/CodexMobile/             iOS SwiftUI 工程
├── scripts/                     本地测试脚本
├── SECURITY.md                  API Key 与发布安全说明
├── CONTRIBUTING.md              贡献指南
└── README.md
```

`dist/`、`.tools/`、`.gradle/`、`build/`、APK、签名文件和本地密钥文件不会提交到仓库。

## Android 本地构建

需要：

- JDK 17
- Android SDK，compileSdk 35
- Gradle 8.x

构建命令：

```powershell
$env:ANDROID_HOME = "你的 Android SDK 路径"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew assembleDebug
```

如果使用仓库本地工具链，可按自己的环境改成对应的 Gradle 路径：

```powershell
$env:ANDROID_HOME = (Resolve-Path ".tools\android-sdk").Path
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\.tools\gradle-8.10.2\bin\gradle.bat assembleDebug
```

构建产物在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## iOS 本地运行

iOS 源码位于 `ios/CodexMobile/`。

1. 在 macOS 上用 Xcode 打开 `ios/CodexMobile/CodexMobile.xcodeproj`。
2. 选择自己的 Team。
3. 按需要修改 Bundle Identifier。
4. 连接真机或模拟器运行。

iOS 真机安装需要 Apple 开发者签名。本仓库是在 Windows 环境生成和发布 Android APK 的，不能在当前环境直接签名打包 IPA。

## 能力测试脚本

仓库提供 `scripts/test-provider-tools.ps1`，用于测试 OpenAI-compatible 第三方接口支持哪些能力。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-provider-tools.ps1 -BaseUrl "https://example.com/v1" -Model "你的模型名"
```

默认会测试：

- `/models`
- Chat Completions
- Responses API
- 流式输出
- hosted `web_search`
- function tools
- JSON object / JSON schema
- 图片输入

生图会消耗额度，默认跳过。确认要测时加：

```powershell
-IncludeExpensive -ImageModel "image-2"
```

脚本会隐藏输入 API Key，不会打印、保存或提交密钥。

## 发布流程

1. 更新版本号：`app/build.gradle` 中的 `versionCode` 和 `versionName`。
2. 构建 APK：`assembleDebug`。
3. 复制 APK 到 `dist/CodexMobile-debug.apk`。
4. 计算 SHA256，写入 release notes。
5. 推送代码和 tag。
6. 在 GitHub Releases 上传 APK。

发布前建议运行密钥扫描：

```powershell
$patterns = 'sk-[A-Za-z0-9_-]{20,}|OPENAI_API_KEY\s*=|Authorization:\s*Bearer\s+[A-Za-z0-9_-]{10,}|Bearer\s+sk-|AIza[0-9A-Za-z_-]{20,}'
git grep -n -I -E $patterns -- . ':!dist' ':!app/build' ':!.gradle' ':!.tools'
```

如果命令没有匹配到密钥类内容，`git grep` 会返回退出码 1，这是正常情况。

## 安全边界

这个项目适合个人自用或自带 Key 场景。移动端本地保存 API Key 无法达到“自有后端代管密钥”的安全级别。

更安全的生产架构是：

```text
手机 App -> 自己的后端 -> OpenAI / 第三方模型服务
```

请不要把真实 API Key、代理 Token、签名证书、keystore、`.env`、生成 APK 或本地工具链提交到仓库。

## 许可证

本仓库采用 [Apache License 2.0](LICENSE) 开源。这个许可证适合客户端应用和二次开发，也保留了清晰的专利授权条款。

仓库中 `app/src/main/assets/vendor/` 下的 DOMPurify、KaTeX 和 marked 属于第三方组件，保留各自许可证与版权声明，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。发布源码包、APK、IPA 或修改版时，请一并保留 `LICENSE`、`NOTICE` 和第三方许可说明。
