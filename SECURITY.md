# Security policy

CyberAuth is in early development. Do not rely on a development build as the sole way to access important accounts.

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Use GitHub's **Report a vulnerability** action in the repository Security tab, or contact the maintainers privately through the Cybercore organization.

Include the affected version, Android version and device, a concise impact description, and reproducible steps. Never include real TOTP seeds, passwords, recovery codes, or personal account data.

## Current security boundaries

- TOTP secrets are encrypted at rest with AES-256-GCM using a non-exportable Android Keystore key.
- The app has no `INTERNET`, SMS, or contacts permission. CameraX adds `ACCESS_NETWORK_STATE`, which does not grant Internet access. Camera permission is requested only after the user starts QR scanning; backup files use Android's system document picker without broad storage permission.
- Portable backups use a user passphrase with PBKDF2-HMAC-SHA256 (600,000 rounds) to derive an AES-256-GCM key. A random salt and nonce are generated for each backup; authenticated decryption completes before imported records are merged into local storage.
- Backup protection depends on the passphrase. Choose a unique passphrase, store it separately from the backup file, and keep an independent recovery method. A forgotten passphrase cannot be reset.
- This milestone has no biometric gate, cloud sync, or independent security audit.
- Device compromise, malicious accessibility services, screenshots, clipboard exposure, and an unlocked device remain outside the protection offered by this early build.
- Clearing app data or uninstalling can make locally stored accounts unrecoverable. Keep each service's recovery codes and another enrolled authenticator available.

Security claims are limited to the behavior actually implemented in the current source; the project has not undergone an independent security audit.
