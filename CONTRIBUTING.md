# Contributing

欢迎提交 issue、建议和 pull request。这个项目的目标是做一个适合个人自带 API Key 使用的移动端聊天 App，因此改动请尽量保持简单、可验证、低风险。

## 开发原则

- 不提交真实 API Key、代理 Token、签名证书、keystore 或 `.env` 文件。
- 不提交 `dist/`、`build/`、`.gradle/`、`.tools/` 或生成的 APK。
- UI 改动需要同时考虑手机、平板、横屏和竖屏。
- 网络接口改动需要兼容 OpenAI 官方接口和常见 OpenAI-compatible 第三方网关。
- 涉及历史记录、附件、图片和 WebView 的改动，要注意长历史性能和内存占用。

## 本地检查

Android 构建：

```powershell
$env:ANDROID_HOME = "你的 Android SDK 路径"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew assembleDebug
```

如果修改了 `app/src/main/assets/chat.html`，建议至少检查脚本语法：

```powershell
node -e "const fs=require('fs'); const html=fs.readFileSync('app/src/main/assets/chat.html','utf8'); const scripts=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]); scripts.forEach(s=>new Function(s)); console.log('ok')"
```

发布或提交前建议扫描密钥：

```powershell
$patterns = 'sk-[A-Za-z0-9_-]{20,}|OPENAI_API_KEY\s*=|Authorization:\s*Bearer\s+[A-Za-z0-9_-]{10,}|Bearer\s+sk-|AIza[0-9A-Za-z_-]{20,}'
git grep -n -I -E $patterns -- . ':!dist' ':!app/build' ':!.gradle' ':!.tools'
```

## Pull Request 建议

- 说明改动目的。
- 列出主要修改文件。
- 说明已做的测试。
- 如果是 UI 改动，建议附截图或录屏。
- 如果是接口兼容性改动，说明测试过的接口模式和模型。

## 安全问题

如果发现密钥泄露、签名材料泄露或可被利用的安全问题，请不要直接公开贴出真实密钥或敏感细节。可以先在 issue 中描述影响范围，或联系仓库维护者处理。
