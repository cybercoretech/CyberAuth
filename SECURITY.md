# Security policy

CyberAuth is in early development. Do not rely on a development build as the sole way to access important accounts.

## Reporting a vulnerability

Please do not publish exploitable details in a public issue. Use GitHub's **Report a vulnerability** action in the repository Security tab, or contact the maintainers privately through the Cybercore organization.

Include the affected version, Android version and device, a concise impact description, and reproducible steps. Never include real TOTP seeds, passwords, recovery codes, or personal account data.

## Current security boundaries

- TOTP secrets are encrypted at rest with AES-256-GCM using a non-exportable Android Keystore key.
- The application currently requests no network, camera, SMS, contacts, or storage permissions.
- This first milestone has no biometric gate, master-password vault, cloud sync, or portable recovery export.
- Device compromise, malicious accessibility services, screenshots, clipboard exposure, and an unlocked device remain outside the protection offered by this early build.
- Clearing app data or uninstalling can make locally stored accounts unrecoverable. Keep each service's recovery codes and another enrolled authenticator available.

Security claims are limited to the behavior actually implemented in the current source; the project has not undergone an independent security audit.
