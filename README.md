# ApiKey Chat

一个给 Android / HarmonyOS 4.2 使用的 OpenAI API key 聊天 App。

功能：

- 手机端保存 API key
- 模型下拉选择，并可刷新 `/v1/models`
- 保存 API key 后隐藏输入框
- 可配置 Responses API 兼容接口地址
- 可切换 `Responses API` / `Chat Completions` 模式，兼容更多第三方接口
- GPT 风格 WebView 消息流
- 本地 Markdown 渲染
- 本地 KaTeX 公式渲染
- 代码块、表格、列表渲染
- 消息复制
- 可折叠思考摘要/推理区：显示接口明确返回的 `reasoning_content`、`reasoning.summary` 或 `<think>...</think>`
- 支持发送图片附件
- 支持发送文件附件
- 支持可配置联网搜索：发送前手动打开“搜索开”，把搜索来源注入模型上下文并在回复下展示来源
- 支持停止当前请求
- 支持基于上一条回复继续写“修改要求”
- 支持编辑上一条消息、重新生成、新聊天
- 本机保存历史聊天
- 历史面板支持打开、删除、接着问
- Chat Completions 模式会带最近上下文继续对话
- 支持 `/v1/images/generations` 生图
- 生图结果保存到 App 本地文件，历史中可继续查看
- 使用 OpenAI Responses API

下载安装包：

- [下载最新版 APK](https://github.com/Melancholy-A/api-key-app-api/releases/latest/download/CodexMobile-debug.apk)
- [查看所有发布版本](https://github.com/Melancholy-A/api-key-app-api/releases)

APK 通过 GitHub Releases 发布，源码仓库不会提交 `dist/`、`build/` 或本地生成的安装包。

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

联网搜索使用提示：

- App 不会默认联网搜索。需要先在设置里填写“搜索接口地址”，发送前点“搜索关/搜索开”按钮，本条消息才会先请求搜索接口。
- 搜索接口支持两种调用方式：地址含 `{query}` 或查询参数时走 GET；否则走 POST JSON。POST 请求体会包含 `query`、`q`、`count`、`num`、`max_results`，便于兼容自有后端或常见搜索服务。
- 鉴权方式可选不鉴权、`Authorization: Bearer`、`X-API-Key` 或 `api_key` 参数；搜索 API key 会用 Android Keystore 加密保存。
- 返回结果会解析 `results`、`data`、`items`、`organic`、`webPages.value` 等常见数组字段，单条结果建议包含 `title`、`snippet`/`content`、`url`、`publishedAt`。
- 搜索失败或没有结果时，App 会提示原因并继续普通聊天；未打开搜索时不会向搜索接口发送请求。

安全说明：这个版本适合个人自用。API key 会用 Android Keystore 加密后保存在本机，但移动端应用仍然不如“手机 App -> 自己后端 -> OpenAI API”的架构安全。
