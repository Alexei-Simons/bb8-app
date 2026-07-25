# BB-8 Controller

An unofficial Android app to control the **Sphero Star Wars BB-8** droid (2015-2018) over Bluetooth Low Energy.

Sphero discontinued the original BB-8, BB-9E, and Sphero Droids apps. They no longer work reliably on modern Android. This project is a from-scratch replacement built for current phones so you can still wake, connect, drive, and check battery health on a real BB-8.

**This app is not made by, endorsed by, or affiliated with Sphero or Lucasfilm.**

---

## What hardware this is for

| Works with | Does not work with |
|------------|-------------------|
| **Sphero BB-8** (app-enabled, Bluetooth LE) | Hasbro RC BB-8 (infrared remote toy) |
| Names appear as `BB-*` when scanning (e.g. `BB-BC60`) | BB-8 on a shelf with a dead battery and no BLE broadcast |
| Sphero charging base (inductive) | Force Band alone (companion accessory, not the droid) |

BB-8 uses the same **Sphero BLE V1** protocol family as Ollie. This app targets BB-8 specifically; other Sphero bots are untested.

---

## Features

- **Scan & connect**: finds nearby `BB-*` devices, no Android pairing required
- **Auto-reconnect**: optional reconnect to the last droid after sleep disconnect
- **Drive**: virtual joystick with speed and heading (150 ms command loop)
- **Heading calibration (aim ring)**: rotate the outer ring so "forward" on the stick matches BB-8's head direction
- **Battery health**: reads voltage, charge cycles, and power state from the droid firmware
- **Diagnostics mode**: disables driving when the battery is critically low but keeps BLE tools available
- **LED colors**: set the main LED from presets (orange, teal, red, blue, white, off)
- **Speed boost**: sends Sphero boost command with cooldown
- **Patrol mode**: square patrol pattern (software-driven)
- **Animations**: experimental animatronic IDs (firmware-dependent)
- **Sensor streaming**: locator position and velocity radar (Sphero V1 async packets)
- **Macro editor**: record drive/LED sequences, host playback, or upload to droid flash
- **Onboarding**: first-run walkthrough for wake, aim, and safety
- **Haptic feedback**: light vibration on connect, commands, and alerts
- **Keepalive**: periodic pings to reduce sleep disconnects while connected
- **Modern UI**: dark theme, connection status, collapsible battery and extras panels

### Roadmap

- [x] Phase 1: Scan, connect, drive
- [x] Phase 2: LED, animations, speed boost, patrol, onboarding
- [x] Phase 3: Macro editor, sensor streaming UI, signed release builds
- [ ] Phase 4: Play Store listing, collision detection, custom icon polish

### Signed releases

Tag a version to build APKs on GitHub Actions:

```bash
git tag v0.3.0
git push origin v0.3.0
```

Optional GitHub secrets for a signed release APK (otherwise release builds use the debug keystore in CI):

| Secret | Purpose |
|--------|---------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

---

## Requirements

- **Phone:** Android 8.0+ (API 26), Bluetooth LE
- **Droid:** Sphero BB-8 with a battery that can hold enough charge to power BLE (see [Troubleshooting](#troubleshooting))
- **Permissions:** Bluetooth scan & connect (requested at launch on Android 12+)

---

## Install (pre-built APK)

Releases will be published on [GitHub Releases](https://github.com/Alexei-Simons/bb8-app/releases) when available.

To build locally, see [Build from source](#build-from-source).

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## How to use

1. **Wake BB-8**: take him off the charging base and shake gently. The internal mechanism should engage; he should show up in a BLE scan.
2. **Open the app** and grant Bluetooth permissions.
3. Tap **Scan for Droids** and select your BB-8 from the list.
4. Wait for **LINKED** on the drive screen.
5. **Calibrate heading**: drag the **outer ring** so the teal arrow points the same way as BB-8's head. This sets which direction "up" on the stick means.
6. **Drive**: drag the **center stick** to move; release to stop.
7. Review the **Battery Health** card for voltage and status.
8. Tap **Disconnect** when finished (cleaner reconnect next time).

### Controls at a glance

| Control | Action |
|---------|--------|
| Outer ring | Aim / heading calibration |
| Center stick | Drive (speed + direction) |
| Reset aim | Sets heading reference back to 0° |
| Disconnect | Ends BLE session |

---

## Battery health

The app queries Sphero's `get_power_state` command and shows:

- **Voltage** (single-cell LiPo, typically ~3.3-4.2 V when healthy)
- **Power state**: `CHARGING`, `OK`, `LOW`, or `CRITICAL`
- **Charge cycles**: lifetime charge count from firmware

Aging BB-8 units often report **CRITICAL** or very low voltage, especially off the charger. Symptoms like a warm shell, instant shutdown, or connect-only-while-docked usually mean the internal LiPo is failing. The sealed shell is not user-serviceable without cutting it open; replacement packs exist but involve destructive surgery. **Do not puncture a swollen battery.**

---

## Troubleshooting

| Problem | Things to try |
|---------|----------------|
| BB-8 not in scan list | Wake off charger; shake; stay within ~3 m; toggle Bluetooth |
| Stuck on "Connecting" | Disconnect, re-seat on charger briefly, wake again, rescan |
| Connects but won't drive | Calibrate the aim ring; ensure battery isn't critically dead |
| Disconnects after ~30 s idle | Should be improved by keepalive; stay connected and retry |
| Battery shows CRITICAL | Cell is likely bad; charger may keep BLE alive but not enough to roll |

---

## Build from source

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended) **or**
- JDK 17+ and Android SDK (API 35)

### Steps

```bash
git clone https://github.com/Alexei-Simons/bb8-app.git
cd bb8-app
```

Create `local.properties` in the project root (not committed):

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

**Windows (PowerShell):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
.\gradlew.bat assembleDebug
```

**macOS / Linux:**

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Protocol references

Clone optional vendor repos for reverse-engineering notes:

```bash
# See vendor/README.md for clone commands
```

Technical details: [docs/PROTOCOL.md](docs/PROTOCOL.md)

---

## Project structure

```
app/src/main/java/com/bb8/app/
  ble/                 BLE GATT client, battery health, keepalive, sensor stream
  sphero/              Packet encoding, async parser, macros, commands
  data/                Preferences, saved macros
  ui/                  Jetpack Compose screens, theme, components
docs/PROTOCOL.md       Sphero V1 BLE notes
vendor/                Third-party protocol references (gitignored clones)
```

**Stack:** Kotlin, Jetpack Compose, Android BLE GATT, Sphero V1 packet protocol

---

## Contributing

Issues and pull requests are welcome on [GitHub](https://github.com/Alexei-Simons/bb8-app).

Please do not submit Sphero copyrighted assets (sounds, animations, branding). This project implements an open protocol interface only.

---

## Author

**[Alexei-Simons](https://github.com/Alexei-Simons)** (Brandon Simons)

---

## License

MIT License. See [LICENSE](LICENSE).

Use at your own risk. You are responsible for safe handling of lithium-polymer batteries and compliance with local laws. Star Wars, BB-8, and Sphero are trademarks of their respective owners.
