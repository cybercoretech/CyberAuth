# CyberAuth threat model — first milestone

## Intended use

CyberAuth is an offline Android TOTP authenticator. It generates RFC 6238 time-based one-time passwords on the device from service-issued shared secrets.

## Data and trust boundaries

The authenticator seed is sensitive. The first implementation stores the account list as AES-256-GCM ciphertext in app-private preferences. Its encryption key is generated and held by Android Keystore; the app does not request Internet access and cannot upload the seeds.

The operating system, Android Keystore implementation, device integrity, and the user’s physical control of the device are trusted. A rooted or compromised device, malicious OS, live memory inspection, or an attacker with access to the unlocked app can defeat these protections.

## Implemented protections

- RFC 6238 TOTP generation with SHA-1, SHA-256, or SHA-512 parameters accepted from `otpauth://` links.
- AES-GCM authenticated encryption at rest, with a fresh random IV for each save.
- Keystore-held AES key is not exported by CyberAuth.
- TOTP QR codes are decoded locally. Camera permission is requested only when scanning is started.
- Portable exports use a separate passphrase, PBKDF2-HMAC-SHA256 (600,000 rounds), random salt and nonce, and AES-256-GCM authenticated encryption.
- Backup restore validates the authenticated payload and account records before merging; it does not replace existing accounts.
- Android backup is disabled for this app; it has no `INTERNET`, SMS, contacts, or broad storage permission. CameraX contributes `ACCESS_NETWORK_STATE`, which does not grant Internet access. Backup file access uses Android's document picker.
- Input validation rejects malformed Base32 keys and unsupported URI parameters.

## Not implemented yet

- Biometric app lock, master-password unlock for the local database, account sync, clipboard auto-clear, Android Autofill integration, SMS consent-based code capture, or CyberVault integration.
- The Android Keystore key currently does not require biometric authentication. It is device-local encryption at rest, not a second factor and not a substitute for a backup.

## Recovery and factor independence

Do not store a service password and its only TOTP seed together if the TOTP is intended to be an independent second factor. Keep service-provided recovery codes somewhere separate. Encrypted backups can restore accounts after migration, but a forgotten backup passphrase or loss of both the device and backup can make entries unrecoverable. Test a restore before relying on a backup.

## SMS

CyberAuth will not request broad SMS-reading permission. If added, SMS code assistance should use Android's explicit, one-message SMS User Consent flow, with manual entry as fallback. SMS delivery itself belongs to the service being authenticated and is not a free CyberAuth feature.
