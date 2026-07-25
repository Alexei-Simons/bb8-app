# Privacy Policy

**Effective date:** July 25, 2026  
**Applies to:** BB-8 Controller Android app (`com.bb8.app`), distributed from [Alexei-Simons/bb8-app](https://github.com/Alexei-Simons/bb8-app)

This document describes what information the app accesses, what it stores on your device, and what it does **not** do.

---

## Summary

BB-8 Controller is designed for **local, on-device operation only**.

- **No accounts** or sign-in
- **No analytics** or advertising SDKs
- **No Internet permission** and no data sent to developer servers
- **No cloud backup** of your droid data by this app

---

## Information the app accesses

### Bluetooth (required)

| Data | Purpose | When |
|------|---------|------|
| Nearby BLE device names (e.g. `BB-BC60`) | Show droids in scan list | While scanning |
| BLE MAC addresses | Connect to a specific droid | Scan and connect |
| GATT characteristics | Control and read the droid | While connected |

Android may treat BLE scan as location-related on some OS versions. This app requests `BLUETOOTH_SCAN` with `neverForLocation` where supported and does not use GPS.

### Device feedback

| Data | Purpose |
|------|---------|
| Vibration (haptics) | Confirm connect, commands, alerts |

---

## Information stored on your device

All storage uses Android **SharedPreferences** on your phone. Nothing is uploaded.

| Key / area | Contents | You can clear by |
|------------|----------|------------------|
| `bb8_prefs` | Onboarding completed flag | App data clear / reinstall |
| `bb8_prefs` | Auto-reconnect preference | In-app toggle |
| `bb8_prefs` | Last connected droid name and BLE address | Disconnect + clear app data |
| `bb8_macros` | Saved macro names and step sequences | Delete macros in app or clear app data |

Macros and last-device data stay on your phone until you remove them or uninstall the app.

---

## Information we do not collect

The developer **does not** receive or store:

- Your name, email, or phone number
- Location coordinates (GPS)
- Contacts, photos, or files
- Crash reports or usage analytics (no Firebase, Sentry, etc.)
- Payment or purchase data

There is **no backend server** operated by this project for the app runtime.

---

## Third parties

### Sphero / your droid

When connected, the app sends standard Sphero BLE V1 commands directly to **your** BB-8 over Bluetooth. That communication is between your phone and the toy. Sphero is not affiliated with this app; their policies may apply to hardware you own.

### GitHub (optional, outside the app)

If you download the APK from GitHub Releases, GitHub's privacy policy applies to that website visit. That is separate from in-app behavior.

---

## Children's privacy

The app does not knowingly collect personal information from anyone. It is a utility for controlling a physical toy and is not directed at children under 13 for data collection purposes (because it collects no personal data).

---

## Data retention and deletion

- **On device:** Data persists until you clear app storage or uninstall.
- **No server copies:** The maintainer has no copy of your macros, device addresses, or usage.

**To delete all app data:**

Android Settings → Apps → BB-8 Controller → Storage → Clear storage  
(or uninstall the app)

---

## Changes to this policy

Material changes will be noted in release notes and committed to this file in the repository. The effective date at the top will be updated.

---

## Contact

Privacy questions: open a [GitHub issue](https://github.com/Alexei-Simons/bb8-app/issues) with the `question` label or contact [@Alexei-Simons](https://github.com/Alexei-Simons).

Maintainer: Brandon Simons ([Alexei-Simons](https://github.com/Alexei-Simons))
