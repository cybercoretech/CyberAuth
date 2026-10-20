# 🛡️ CyberAuth

**Offline two-factor codes, with a Cybercore edge.** CyberAuth is an independent, open-source Android authenticator inspired by the clear, private, offline-first experience of apps such as Proton Authenticator. It does not copy Proton code or assets.

> ⚠️ **Early development.** This is a first implementation milestone, not an audited production authenticator. Do not make it the only way to access important accounts yet. Keep recovery codes and another enrolled authenticator available.

## ✨ What works now

- 🔐 RFC 6238 TOTP codes generated locally (SHA-1, SHA-256, and SHA-512).
- 🧩 Add an account with a Base32 setup key or paste an `otpauth://totp/` link.
- ⏱️ Live expiry countdown and one-tap code copy.
- 🗝️ Account data encrypted at rest using AES-256-GCM and an Android Keystore key.
- 📴 No Internet, camera, SMS, contacts, or storage permission in the current app.
- 🚫 Android cloud backup disabled so encrypted account data is not silently copied to a cloud backup.

## 🧭 Next milestones

1. QR import with a camera permission requested only when scanning.
2. Optional biometric app lock, backed by AndroidX BiometricPrompt and a Keystore key requiring user authentication.
3. Clearly explained encrypted export/restore and migration recovery.
4. Clipboard auto-clear, search, issuer icons, accessibility polish, and broader TOTP compatibility tests.
5. Optional CyberVault integration, designed so authenticator seeds do not silently collapse the independence of a second factor.
6. SMS assistance only through explicit Android SMS User Consent, never broad SMS inbox access; manual entry remains available.

## 🏗️ Build

Requirements: Android Studio with JDK 17 or newer and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. This is a development build. Signed public releases and their verification instructions will be added only after release signing and recovery behavior are ready.

## 🔒 Security model

Read [the threat model](docs/THREAT_MODEL.md) and [security policy](SECURITY.md). Current local encryption does not protect seeds from a compromised device or someone using the unlocked app. It also has no portable recovery path yet: clearing app data, uninstalling, or losing the Keystore key can make entries unrecoverable. Keep service recovery codes separately.

CyberAuth does not yet provide a password vault, cloud sync, biometric gate, SMS sending, or a way to recover a lost device. Do not treat TOTP stored alongside a password in the same future vault as an independent second factor.

## 🧪 Reference and licensing

Proton Authenticator is a product/UX reference only. CyberAuth is independently implemented and does not copy its source code or artwork. Proton's Android authenticator is GPL-3.0-or-later; this repository is MIT-licensed. See [LICENSE](LICENSE).

## 🌐 Project

- **Project site:** [cybercoretech.github.io/CyberAuth](https://cybercoretech.github.io/CyberAuth/)
- **Source and issues:** [github.com/cybercoretech/CyberAuth](https://github.com/cybercoretech/CyberAuth)
- **License:** MIT
