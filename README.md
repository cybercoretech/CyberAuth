# 🛡️ CyberAuth

**Offline two-factor codes, with a Cybercore edge.** CyberAuth is an independent, open-source Android authenticator inspired by the clear, private, offline-first experience of apps such as Proton Authenticator. It does not copy Proton code or assets.

> ⚠️ **Early development.** This is a first implementation milestone, not an audited production authenticator. Do not make it the only way to access important accounts yet. Keep recovery codes and another enrolled authenticator available.

## ✨ What works now

- 🔐 RFC 6238 TOTP codes generated locally (SHA-1, SHA-256, and SHA-512).
- 🧩 Add an account by scanning a TOTP QR code, entering a Base32 setup key, or pasting an `otpauth://totp/` link.
- ⏱️ Live expiry countdown and one-tap code copy.
- 🗝️ Account data encrypted at rest using AES-256-GCM and an Android Keystore key.
- 📴 No Internet permission, SMS, or contacts permission. Android's system document picker handles backups without broad storage access. CameraX adds `ACCESS_NETWORK_STATE`, which does not grant Internet access.
- 📷 Camera access is requested only when you choose **Scan QR**. QR decoding happens on-device.
- 📦 Export a portable backup encrypted with a separate passphrase; restore it on another device through Android's file picker. Imports merge accounts and never replace existing entries.
- 🧪 Backup format uses PBKDF2-HMAC-SHA256 (600,000 rounds) and AES-256-GCM with a random salt and nonce; file metadata is authenticated.
- 🚫 Android cloud backup disabled so encrypted account data is not silently copied to a cloud backup.

## 🧭 Next milestones

1. Optional biometric app lock, backed by AndroidX BiometricPrompt and a Keystore key requiring user authentication.
2. Clipboard auto-clear, search, issuer icons, accessibility polish, and broader TOTP compatibility tests.
3. Optional CyberVault integration, designed so authenticator seeds do not silently collapse the independence of a second factor.
4. SMS assistance only through explicit Android SMS User Consent, never broad SMS inbox access; manual entry remains available.

## 🏗️ Build

Requirements: Android Studio with JDK 17 or newer and Android SDK 36.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. This is a development build. Signed public releases and their verification instructions will be added only after release signing and recovery behavior are ready.

## 🔒 Security model

Read [the threat model](docs/THREAT_MODEL.md) and [security policy](SECURITY.md). Local encryption does not protect seeds from a compromised device or someone using the unlocked app. To migrate devices, export an encrypted backup and store its passphrase separately. CyberAuth cannot recover a forgotten backup passphrase. Keep service recovery codes separately, and test restoring a backup before relying on it.

CyberAuth does not yet provide a password vault, cloud sync, biometric gate, SMS sending, or a way to recover a lost device. Do not treat TOTP stored alongside a password in the same future vault as an independent second factor.

## 🧪 Reference and licensing

Proton Authenticator is a product/UX reference only. CyberAuth is independently implemented and does not copy its source code or artwork. Proton's Android authenticator is GPL-3.0-or-later; this repository is MIT-licensed. See [LICENSE](LICENSE).

## 🌐 Project

- **Project site:** [cybercoretech.github.io/CyberAuth](https://cybercoretech.github.io/CyberAuth/)
- **Source and issues:** [github.com/cybercoretech/CyberAuth](https://github.com/cybercoretech/CyberAuth)
- **License:** MIT
