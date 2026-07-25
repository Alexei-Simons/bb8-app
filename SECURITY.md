# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| Latest [GitHub Release](https://github.com/Alexei-Simons/bb8-app/releases) | Yes |
| Older releases | Best effort only |
| `main` branch | Development; install at your own risk |

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you believe you have found a security issue in BB-8 Controller:

1. Go to the repository **Security** tab: [Report a vulnerability](https://github.com/Alexei-Simons/bb8-app/security/advisories/new)
2. Or contact the maintainer privately via GitHub: [@Alexei-Simons](https://github.com/Alexei-Simons)

Include:

- Description of the issue and potential impact
- Steps to reproduce (APK version or commit hash)
- Any proof-of-concept if available

We aim to acknowledge reports within **7 days** and provide a fix or mitigation plan as soon as practical.

## Scope

In scope:

- Vulnerabilities in this app's source code or distributed APKs built from this repository
- Unsafe defaults that could harm users or their devices (for example, bypassing battery safety gates)

Out of scope:

- Vulnerabilities in Android, Sphero firmware, or third-party libraries outside this repo (please report to the vendor)
- Social engineering or physical access to an unlocked phone
- Issues requiring a modified or unofficial APK not published by this project

## Safe disclosure

We support coordinated disclosure. We will credit reporters in release notes when they wish to be named.

## Security practices in this project

- No `INTERNET` permission: the app does not open network connections.
- Bluetooth permissions are used only for local BLE communication with your droid.
- Dependencies are pinned via Gradle version catalogs; Dependabot monitors updates.
- Release APKs are published through [GitHub Releases](https://github.com/Alexei-Simons/bb8-app/releases) from tagged sources.

## LiPo battery safety

BB-8 contains a lithium-polymer battery. This app reads voltage and power state but cannot prevent hardware failure. If your droid is warm, swollen, or shuts down instantly, stop using it and follow hardware safety guidance in [README](README.md#battery-health). **Do not puncture a damaged battery.**
