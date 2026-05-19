# Security Notes

This project is designed for bring-your-own-key usage. Do not commit real API keys, proxy tokens, signing keys, generated APKs, or local Android SDK/Gradle toolchains.

The Android app stores the API key entered on the phone in app-private SharedPreferences, encrypted with Android Keystore AES-GCM. That stored value is runtime device data and is not part of the source repository or generated source files.

Before publishing, run a secret scan and keep `.gitignore` active. If a real key is ever committed, revoke it immediately from the provider dashboard and create a new key.
