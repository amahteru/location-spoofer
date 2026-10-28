# LocationSpoofer

[简体中文](README.md) | **English**

An LSPosed module for location spoofing on Android that also simulates nearby Wi-Fi, cell towers, Bluetooth, satellites and step counts, so the location and its surroundings stay consistent.

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org)
[![libxposed](https://img.shields.io/badge/libxposed-API%20100%2B-purple.svg)](https://github.com/libxposed/api)

## Features

**Location**
- Fixed-point spoofing: pick a point on the map or search for a place.
- Route spoofing: multi-point routes, optional real road planning through AMap, and 15-second stops at some intersections to imitate traffic lights. Runs as an automatic loop or under manual control with a floating joystick.
- Speed presets: walking, running, cycling, driving, or a custom speed.
- Motion realism: speed fluctuation, altitude variation, cadence changes, and optional slight position jitter.

**Surroundings**
- Wi-Fi: scan results, current connection info, saved networks, DHCP info.
- Cellular: `CellInfo`, `CellLocation`, operator, `ServiceState`, signal strength, and `PhoneStateListener` / `TelephonyCallback` callbacks.
- Bluetooth: BLE scan results.
- Satellites: `GnssStatus` satellite lists, NMEA sentences, and the satellite counts attached to `Location`.
- Steps: step counter and step detector sensors.
- Data sources: on-site collection (points or routes, no root required) and imports from WiGLE and OpenCellID. When several collected points are nearby, their data is blended by distance.

**Stealth**
- Rewrites the callbacks of the AMap, Baidu and Tencent location SDKs directly.
- Clears the mock flags on `Location` and intercepts mock-location queries through AppOps and `Settings.Secure`.

**App**
- Map engines: AMap, Baidu Maps and Google Maps; AMap is used by default. Google Maps needs your own API key.
- Data management: view, edit and delete collected data; import and export as JSON.
- Material Design 3 interface with dynamic color; Simplified Chinese and English.

## Requirements

- Android 13 (API 33) or later on an arm64-v8a device
- Root (KernelSU, APatch or Magisk)
- LSPosed, or another framework that supports libxposed API 100+

## Installation

Download `LocationSpoofer-scoped-arm64-v8a-*.apk` from [Releases](https://github.com/amahteru/location-spoofer/releases), or build it yourself:

```bash
git clone https://github.com/amahteru/location-spoofer.git
cd location-spoofer
./gradlew installDebug
```

Building requires JDK 17 and the Android SDK (API 37). To use Google Maps, provide a key through the `GOOGLE_MAPS_API_KEY` environment variable or a `local.yml` file in the project root. For Windows, see [docs/development-windows.md](docs/development-windows.md) (Chinese).

## Usage

1. Grant LocationSpoofer root access in your root manager.
2. Enable the module in LSPosed, select the apps to spoof in its scope (for example WeChat, DingTalk, Chaoxing, AMap), then force-stop those apps or reboot.
3. **Location** tab: choose a position, tap "Simulate here", select the Wi-Fi, cell and Bluetooth data to simulate under "Simulation options", then tap "Start simulation".
4. **Route** tab: add waypoints, choose the run mode and speed, then tap "Start route simulation".
5. **Features** tab: "Collect local data", "Manage collected data", "Import & export data".
6. **Info** tab: map settings, motion realism, root solution, background keep-alive and more.

Starting a simulation restarts the target apps by default so the new configuration takes effect immediately; you can turn this off under "Simulation options".

## Structure

| Module | Description |
|---|---|
| `app` | App entry point and packaging; aggregates dependency injection from all modules |
| `app-ui` | Jetpack Compose UI and ViewModels |
| `service` | Foreground service, floating joystick, recovery after boot |
| `xposed` | LSPosed entry point `LocationHooker` and all hooks |
| `core-data` | Room database, repositories, configuration publishing, environment scanning |
| `core-geo` | Pure Kotlin module: coordinate conversion, route geometry, motion model |

The app publishes configuration snapshots to the target app processes through libxposed remote preferences, switching to a framework-managed remote file when a snapshot exceeds 128 KiB. Hooks only read an in-memory copy of the configuration. No SELinux policy changes or writes to shared temporary directories are needed.

## Disclaimer

This project is for learning, research and lawful development testing only. Do not use it for anything illegal or against a platform's terms of service, including but not limited to fake check-ins, exam cheating or commercial fraud. You bear all consequences of using it, including account bans, data loss and legal disputes.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
