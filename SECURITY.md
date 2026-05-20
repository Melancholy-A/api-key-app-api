# Security Policy

ApiKey Chat is a bring-your-own-key mobile app. It is suitable for personal use, testing, and self-hosted compatible API workflows, but it should not be treated as a high-security key custody product.

## Key Storage

- Android stores API keys in app-private SharedPreferences encrypted with Android Keystore AES-GCM.
- iOS stores API keys in Keychain.
- Stored keys are runtime device data. They are not generated source files and should never be committed to this repository.

## Do Not Commit

- Real API keys or proxy tokens
- Signing keys, keystores, provisioning files, or certificates
- `.env`, `secrets.properties`, `keystore.properties`, `signing.properties`
- Generated APK / AAB / IPA files
- Local Android SDK, Gradle toolchains, or build caches

## Safer Production Architecture

For public or team distribution, prefer this architecture:

```text
Mobile app -> Your backend -> OpenAI / compatible provider
```

That keeps provider keys on your server and lets you add rate limits, abuse detection, audit logs, and user-level access control.

## Before Publishing

Run a secret scan before pushing or creating a release:

```powershell
$patterns = 'sk-[A-Za-z0-9_-]{20,}|OPENAI_API_KEY\s*=|Authorization:\s*Bearer\s+[A-Za-z0-9_-]{10,}|Bearer\s+sk-|AIza[0-9A-Za-z_-]{20,}'
git grep -n -I -E $patterns -- . ':!dist' ':!app/build' ':!.gradle' ':!.tools'
```

If a real key is ever committed, revoke it immediately from the provider dashboard and create a new key. Rewriting git history alone is not enough once a secret has been pushed.
