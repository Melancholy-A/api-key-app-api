# Phase 4-5 Verification Report

Date: 2026-08-27

## Scope

- Phase 4: connection diagnostics from Settings > Connection.
- Phase 5: retry for recoverable failed chat requests.

## Delivered APK

- Version: `1.9.0` (`versionCode 88`)
- File: `03_Output/CodexMobile-1.9.0-debug.apk`
- SHA-256: `55DF5996718EAEC7CE503D064B43F0C55314BEEB426AEAF346D81157A88AF3E2`
- Compatibility target: Android API 26+; the final network and UI checks require a real Huawei/HarmonyOS device.

## Automated Verification

```text
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug
BUILD SUCCESSFUL
```

- Unit tests: 32 total, 0 failures, 0 errors.
- Chat math normalization script: passed.
- Lint: 0 errors, 34 existing warnings outside this phase's behavior.
- `git diff --check`: no whitespace errors.
- Sensitive-value scan excluding test/build/Git data: no files matched the common `sk-...` credential pattern.

## Covered Behaviors

- A successful connection check reports normalized URL, elapsed time, model count, and selected-model availability.
- 403 region restrictions and TLS errors receive specific guidance.
- Diagnostic error rendering redacts bearer credentials before display.
- Retry is offered for TLS, DNS/connection/timeout, 429, and selected 5xx/524 failures.
- Retry is not offered after cancellation, 401 authentication failures, or 403 permission/region failures.

## Required Manual Device Checks

1. Install the delivered APK and open Settings > Connection.
2. Save a known-valid API address/key, then tap `检测连接`.
   Expected: normalized address, HTTP 200, elapsed time, model count, and selected-model status. The key must not appear in the result.
3. Test an invalid key.
   Expected: a 401 explanation and no exposed credential.
4. Test a provider/address that returns a 403 region or permission error.
   Expected: a targeted explanation and no retry control.
5. Trigger a transient DNS, TLS, timeout, 429, 503, or 524 failure.
   Expected: the prompt and attachments return to the composer and the small retry icon appears.
6. Tap the retry icon.
   Expected: the same request is re-sent without an additional user-message bubble.
7. Stop an in-progress response.
   Expected: prompt/attachments return to the composer, but no retry icon is shown.
8. After a transient failure, edit the prompt or change attachments.
   Expected: the retry icon disappears because the saved request is no longer current.

## Limitation

Local checks cannot prove a third-party provider's live TLS policy, regional rules, response time, or HarmonyOS-specific rendering. Those behaviors need the device checks above.
