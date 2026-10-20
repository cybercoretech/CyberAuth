## CyberAuth {{VERSION}} — early access

Signed Android APK for Android 8.0 and newer.

### Install

Download [CyberAuth.apk](https://github.com/cybercoretech/CyberAuth/releases/download/v{{VERSION}}/CyberAuth.apk), verify it with the published [SHA256SUMS](https://github.com/cybercoretech/CyberAuth/releases/download/v{{VERSION}}/SHA256SUMS), then open the APK on your Android device and follow the installer prompt. Android may ask you to allow installs from your browser or file manager.

For update notifications, add `https://github.com/cybercoretech/CyberAuth` to Obtainium.

### Verify the signing certificate

The APK is signed with the project's release key. Its SHA-256 certificate fingerprint is in [SIGNING_CERTIFICATE_SHA256.txt](https://github.com/cybercoretech/CyberAuth/releases/download/v{{VERSION}}/SIGNING_CERTIFICATE_SHA256.txt). Future releases will use the same signing key.

### Security status

This is an early-access authenticator and has not had an independent security audit. Keep service recovery codes and another sign-in method available. The app does not yet provide biometric app lock or cloud sync. Read the [threat model](https://github.com/cybercoretech/CyberAuth/blob/main/docs/THREAT_MODEL.md) before relying on it as your only way to access important accounts.
