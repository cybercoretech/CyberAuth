# Contributing to CyberAuth

CyberAuth handles authentication secrets, so correctness and privacy take priority over feature speed.

- Keep code generation offline and avoid analytics or network calls in the authenticator path.
- Add tests for TOTP vectors and parser changes; never use personal account seeds in tests, screenshots, issues, or logs.
- Do not add broad SMS, contacts, or storage permissions. Request sensitive access only in the user flow that needs it.
- Document security boundaries and recovery consequences whenever storage or unlock behavior changes.
- Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before proposing a change.
- Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).
