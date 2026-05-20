# ApiKey Chat

一个给 Android / HarmonyOS 4.2 使用的 OpenAI API key 聊天 App，同时提供 iOS SwiftUI 源码工程。

功能：

- 手机端保存 API key
- 模型下拉选择，并可刷新 `/v1/models`
- 保存 API key 后隐藏输入框
- 可配置 Responses API 兼容接口地址
- 可切换 `Responses API` / `Chat Completions` 模式，兼容更多第三方接口
- 接近 ChatGPT 手机端的白底消息流、浅灰用户气泡和底部圆角输入栏
- 本地 Markdown 渲染
- 本地 KaTeX 公式渲染
- 代码块、表格、列表渲染
- 每条回复支持复制、朗读、分享、重新生成
- 可折叠思考过程/推理区：显示接口明确返回的 `reasoning_content`、`reasoning.summary`、工具执行摘要或 `<think>...</think>`
- 支持发送图片附件
- 支持发送文件附件
- 支持智能体自动工具模式：主界面不再需要搜索按钮，Responses API 会按需调用 `web_search`，也可由模型请求 App 侧 `open_url`、`custom_search` 和可选 `generate_image`
- 保留备用本地搜索接口：不配置接口时自动尝试内置 DuckDuckGo Lite / Bing，也可填写自定义搜索 API
- 内置搜索会针对万方/知网/维普等检索意图做站点定向搜索，并要求模型直接整理搜索结果而不是只给检索方法
- 明确指定万方/知网/维普时，会限制来源域名，避免混入普通网页冒充数据库检索结果
- 搜索和普通请求会持有短时后台唤醒锁，小窗/切屏时继续执行
- 所有接口模式都会带本地对话上下文，历史聊天打开后可继续问
- 每条用户消息下方有小图标入口，可对该条消息补充“修改要求”后直接发送继续问
- 手机/平板、横屏/竖屏会自动重排顶部区域，底部工具按钮区支持一键收起
- 网页查看器在竖屏会使用紧凑控制栏，并支持“适配/原始”布局切换
- 支持 App 内网页查看器：点击搜索来源或在输入框填网址后点“网页”即可打开
- 支持整段聊天分享
- 设置面板内容较长时可上下滑动，检查更新按钮固定保留在设置底部区域
- 支持本机保存个人指令/长期偏好，发送时自动带给模型
- 支持检查 GitHub Releases 更新、下载 APK 并唤起系统安装器
- 自动更新下载时会显示百分比和已下载大小
- 支持停止当前请求
- 支持基于上一条回复继续写“修改要求”
- 支持编辑上一条消息、重新生成、新聊天
- 本机保存历史聊天
- 历史面板支持搜索、打开、删除、接着问
- Chat Completions 模式会带最近上下文继续对话
- 支持 `/v1/images/generations` 生图
- 生图结果保存到 App 本地文件，历史中可继续查看
- 生成图片会进入本地图片库，可从底部工具栏重新插入查看
- 使用 OpenAI Responses API

下载安装包：

- [下载最新版 APK](https://github.com/Melancholy-A/api-key-app-api/releases/latest/download/CodexMobile-debug.apk)
- [查看所有发布版本](https://github.com/Melancholy-A/api-key-app-api/releases)

APK 通过 GitHub Releases 发布，源码仓库不会提交 `dist/`、`build/` 或本地生成的安装包。

iOS 版：

- iOS 源码在 `ios/CodexMobile/`，用 macOS 上的 Xcode 打开 `ios/CodexMobile/CodexMobile.xcodeproj`。
- 第一次运行前，在 Xcode 里选择自己的 Team，并按需要把 Bundle Identifier 改成自己的唯一标识。
- iOS 版支持 API key / Base URL / 模型选择、Responses API / Chat Completions、历史聊天搜索与继续问、图片和文件附件、智能体自动工具、个人指令、自定义搜索接口、App 内网页、生图、停止请求、每条用户消息下方的小图标修改要求、回复朗读和分享。
- API key 和搜索 API key 使用 iOS Keychain 保存在本机，源码仓库不会包含任何 key。
- 当前仓库是在 Windows 环境生成并上传的，不能直接在这里签名打包 IPA；iOS 真机安装需要 macOS + Xcode + Apple 开发者签名。

安装：

1. 把 APK 传到华为手机。
2. 在文件管理器里点开安装。
3. 如果系统提示拦截，给文件管理器开启“安装未知应用”权限，或按提示处理纯净模式/增强防护。
4. 打开 App，输入 OpenAI API key，点“保存”，选择模型后发送消息。

如果直连 OpenAI 返回 `Country, region, or territory not supported`，说明当前网络位置不在 OpenAI API 支持范围内。App 不会绕过这个限制；你可以在设置里填写自己有权使用的合规后端或 Responses API 兼容接口地址。

第三方 API 使用提示：

- 接口地址填基础地址，例如 `https://example.com/v1`，不要只填域名，也不要必须带 `/chat/completions`；App 会自动剥掉 `/responses`、`/chat/completions`、`/models` 这些尾巴。
- 如果第三方接口不支持 `/v1/responses`，在设置里把接口模式切到 `Chat Completions`。
- `Read error ... TLSV1_ALERT_INTERNAL_ERROR` 是 HTTPS/TLS 层错误，通常是第三方服务的证书链、SNI、TLS 版本、域名或网关配置问题，不是模型返回内容错误。
- `Chat Completions` 模式下图片按常见 OpenAI-compatible 格式发送；文件会作为 data URL 文本附带，具体能否理解取决于第三方模型/网关。

联网搜索/自动工具使用提示：

- 默认推荐使用 `Responses API + 自动工具模式`。模型会按问题自己判断是否需要联网搜索或打开网页。
- 如果第三方接口的 hosted `web_search` 不稳定，可以关闭自动工具，改用备用本地搜索接口。
- “搜索接口地址”可以留空，留空时会自动尝试内置 DuckDuckGo Lite 和 Bing；需要自有搜索服务时再填写地址。
- 如果只想指定内置源，可以填 `builtin:duckduckgo` 或 `builtin:bing`。
- 搜索接口支持两种调用方式：地址含 `{query}` 或查询参数时走 GET；否则走 POST JSON。POST 请求体会包含 `query`、`q`、`count`、`num`、`max_results`，便于兼容自有后端或常见搜索服务。
- 鉴权方式可选不鉴权、`Authorization: Bearer`、`X-API-Key` 或 `api_key` 参数；搜索 API key 会用 Android Keystore 加密保存。
- 返回结果会解析 `results`、`data`、`items`、`organic`、`webPages.value` 等常见数组字段，单条结果建议包含 `title`、`snippet`/`content`、`url`、`publishedAt`。
- 回复下方的“联网搜索来源”可以直接点击，会在 App 内置网页查看器中打开；也可以在输入框输入网址后点顶部“网页”按钮。

更新说明：

- 设置面板里点“检查更新”会读取本仓库 GitHub Releases 的最新版 APK。
- 下载使用 Android 系统 DownloadManager，切到小窗或后台也会继续下载；App 顶部状态条会显示下载百分比和已下载大小，下载完成后会唤起系统安装器。
- Android 8 及以上如果首次安装更新被拦截，需要给本应用开启“安装未知应用”权限。
- 搜索失败或没有结果时，App 会提示原因并继续普通聊天；未打开搜索时不会向搜索接口发送请求。

第三方接口工具能力测试：

- 仓库提供了本地脚本 `scripts/test-provider-tools.ps1`，用来判断你的第三方 OpenAI-compatible 接口是否支持 Codex 类似的工具调用。
- 运行示例：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-provider-tools.ps1 -BaseUrl "https://remix.codes/v1" -Model "你的模型名"
```

- 默认会测试 `/models`、普通聊天、Responses、流式输出、内置 `web_search`、function tools、JSON 输出、图片输入等低成本能力。
- 生图会消耗额度，默认跳过；确认要测时再加 `-IncludeExpensive`，也可以用 `-ImageModel "image-2"` 指定生图模型。
- 脚本会隐藏输入 API key，不会打印、保存或提交 key。
- 结论 A 表示支持 Responses 内置 `web_search`；结论 B 表示只支持 function calling，需要 App 自己执行搜索；结论 C 表示只能由 App 自己判断是否搜索并外挂搜索 API。

安全说明：这个版本适合个人自用。API key 会用 Android Keystore 加密后保存在本机，但移动端应用仍然不如“手机 App -> 自己后端 -> OpenAI API”的架构安全。
