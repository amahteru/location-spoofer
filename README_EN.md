<div align="center">

<h1>LocationSpoofer</h1>

<p>Android system-level location spoofing and wireless environment simulation framework based on KernelSU + LSPosed</p>

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![KernelSU](https://img.shields.io/badge/Root-KernelSU-orange.svg)](https://kernelsu.org)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20101%2B-purple.svg)](https://github.com/LSPosed/LSPosed)

[简体中文](README.md) | [English](README_EN.md)

</div>

---

## Introduction

In modern Android risk control and anti-cheating environments, standard developer options ("Mock Location") have long been classified as a critical risk factor by commercial positioning and fraud detection SDKs (such as AMap Security, Tencent Positioning, Baidu Maps SDK, NetEase EasyShield, and enterprise attendance verification systems). These detection frameworks do not merely check the `isFromMockProvider` / `isMock` API flags; they actively collect and cross-verify ambient physical signals:

*   **Nearby Wi-Fi access points and BSSID lists** (cross-checked against global Wi-Fi fingerprint databases)
*   **Cellular base stations** (GSM / WCDMA / LTE / 5G NR cell identifiers and carrier profiles)
*   **Local Bluetooth BLE beacons**
*   **Underlying GNSS satellite constellations and visible ephemeris matrices**
*   **Accelerometer and step-counter hardware sensor linkage**
*   Time-series coordinate analysis (FFT and variance checks) to detect artificial static coordinates or deterministic linear trajectories.

**LocationSpoofer** is a **system-level virtual positioning and wireless environment simulation framework** engineered specifically to counter deep anti-cheating detections.
Powered by **KernelSU / APatch / Magisk** for root privileges and **LSPosed (libxposed API 101+)** to hook target processes in the Zygote stage, LocationSpoofer intercepts and fakes all positioning, wireless networking, and motion sensor API responses with physical fidelity. This guarantees that target apps obtain self-consistent, realistic location and radio fingerprints without detecting virtualization.

---

## Features

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             LocationSpoofer                             │
│        System-Level Location & Wireless Environment Simulation          │
└─────────────────────────────────────────────────────────────────────────┘
        │                            │                            │
        ▼                            ▼                            ▼
  【Spatial & Physics】        【Radio Environment】          【Hiding the Simulation】
  • Triple Map Engine          • Wi-Fi Scan & Connection      • Nothing Injected into Apps
  • WGS-84/GCJ-02/BD-09 Convert• 2G-5G NR Cell Towers         • isFromMockProvider Erased
  • Ornstein-Uhlenbeck Jitter  • BLE Beacons Filtering        • AppOps OP_MOCK_LOCATION Masked
  • Speed & Altitude Realism   • WiGLE / OpenCellID Import    • Settings.Secure Key Override
  • Real Road Snapping         • Spatial IDW Interpolation
  • Floating Joystick
```

### 1. Multi-Map Engines & Coordinate Adaptation
* **Triple Engine Support**: Natively integrates AMap 3D, Baidu Maps, and Google Maps for flexible worldwide POI search and road-network routing.
* **Out-of-the-Box & Multilingual Support**: Default "Auto Match" smoothly uses AMap across all languages (including Chinese, English, Arabic, etc.), functioning without requiring Google Play Services or proxy setups.
* **Custom Google API Key**: No default Google Maps API Key is embedded. To use Google Maps rendering and Places global search, simply input your own Google API Key in the Settings page (enable Maps SDK for Android and Places API (New) for it in Google Cloud).
* **Coordinate Conversion**: Points are picked in `GCJ-02` on AMap / Baidu Maps, while the system location service outputs standard `WGS-84` coordinates and satellite metadata as Android specifies; each app converts to the system it needs, avoiding double-encryption offsets of hundreds of meters.
* **Zero-Latency Computation**: Coordinate transformations are pre-computed in memory and fetched directly inside Xposed hooks, avoiding repeated trigonometric calculations in high-frequency callback loops.

### 2. GPS Physics Jitter & Motion Realism
Real GPS receivers naturally output coordinates with Gaussian white noise caused by ionospheric scintillations, multipath reflections, and clock offsets.
* **Ornstein-Uhlenbeck Process**: Implements a mean-reverting physical random walk model:

  $$\mathrm{d}X_t = -\alpha X_t \mathrm{d}t + \sigma \mathrm{d}W_t$$

  where $\sigma$ is the noise intensity and $\alpha$ is the mean-reversion coefficient (configured to `0.05` to pull drift back by 5% per second). This produces physical, low-frequency drift while strictly bounding displacement within 3-Sigma limits (clamped to 4 meters maximum) to prevent sudden location jumps.

* **Altitude & Accuracy (GDOP) Drift**: Horizontal accuracy and altitude fluctuate dynamically to simulate changing satellite geometries and tropospheric delays.

### 3. Hiding the Simulation
* **Nothing Injected into Apps**: All hooks live in `system_server`, the phone process and the Bluetooth process. Target apps contain no hook code, so in-app anti-cheat SDKs have no hook framework to detect.
* **Mock Flag Eraser**:
  * Locations handed out by the system location service have `isFromMockProvider` / `isMock` cleared and the `mockLocation` extra removed;
  * Intercepts `AppOpsService`'s `OP_MOCK_LOCATION (58)` checks to return `MODE_IGNORED (1)`;
  * Intercepts `Settings.Secure` queries for `mock_location` and `allow_mock_location` to return `0`.
* **System and Self Untouched**: This app, telephony (including emergency location) and system components other than GMS always receive the real location; once simulation stops, leftover simulated cache entries are not handed to any app.

### 4. Wi-Fi, Cell Tower & Bluetooth Simulation
* **On-Site Environment Scanner**: Background sweep utility scans and logs physical Wi-Fi APs (SSID/BSSID/RSSI/frequency/channel/standard), Cell Towers (GSM, WCDMA, CDMA, LTE, 5G NR configurations with MCC/MNC/LAC/CID/TAC/PCI/NCI and dBm signals), and BLE Bluetooth beacons.
* **Spatial Inverse Distance Weighting (IDW)**: As coordinates move along a path, the engine retrieves recorded points within 50 meters from the Room database and applies inverse square distance weights:

  $$w_i = \frac{1}{d_i^2}$$

  to smoothly interpolate Wi-Fi RSSI and cell dBm levels, avoiding abrupt signal jumps.
* **Fine-Grained Radio Management**: Manually select specific Wi-Fi APs, Cell Towers, or Bluetooth beacons to broadcast during simulation.
* **Full Wireless API Coverage**:
  * Hooks `WifiManager.getScanResults()`, `getConnectionInfo()`, `getConfiguredNetworks()`, `getDhcpInfo()`;
  * Hooks `TelephonyManager.getAllCellInfo()`, `getCellLocation()`, `getNetworkOperator()`, `getServiceState()`, `getSignalStrength()`, `PhoneStateListener`, `TelephonyCallback`;
  * Uses legitimate manufacturer OUIs (TP-Link, Huawei, Xiaomi, Cisco, etc.) for non-scanned areas.
* **Cloud WiGLE & OpenCellID Integration**: Pulls real-world Wi-Fi and cell tower records around any coordinate globally and stores them locally for offline replay.

### 5. Satellite Matrix & NMEA Generation
* **GnssStatus Matrix Injection**: Hooks `GnssStatus` to simulate 20+ active satellites (GPS, BeiDou, GLONASS) with valid PRNs, signal-to-noise ratios (CNR/SNR), azimuths, elevations, and `usedInFix` flags.
* **Dynamic NMEA Protocol Streaming**: Pushes NMEA to target apps' listeners from the system location service, dynamically generating compliant raw `$GPGGA`, `$GPRMC`, `$GPGSA`, `$GPGSV` sentences in memory with accurate checksums.
* **Satellite Metadata Keep-Alive**: Injects `satellites=20`, `satellites_in_view=20`, `satellites_used_in_fix=18` into `Location.getExtras()` to prevent map SDKs from discarding GPS signals due to missing satellite locks.

### 6. Route Planning & Traffic Light Simulation
* **Real Road Snapping**: Multi-point routing algorithms snap trajectories to physical streets, preventing straight-line navigation through buildings.
* **Traffic Light Waits**: Automatically identifies intersections and pauses for 15 seconds at traffic light nodes before resuming smooth acceleration.
* **Compose Floating Joystick**: Overlay joystick with speed controls (0 ~ 10 m/s) and steering damping adjustments for live manual navigation.

### 7. User Interface
* Built with Jetpack Compose, following Material Design 3 with system dynamic color support and a decoupled modular architecture.

---

## System Architecture

Built on modern **MVVM + Clean Architecture**, split into 6 Gradle modules by responsibility:

| Module | Type | Responsibility |
|---|---|---|
| `app` | Android App | The host shell: `Application` / `MainActivity`, signing & packaging config, aggregates every module's Koin DI; only bundles the `xposed` module's artifact (the `LocationHooker` class and `META-INF/xposed/*` metadata) into the final APK for LSPosed to scan — it never calls that code directly |
| `app-ui` | Android Library | All Jetpack Compose UI: screens, dialogs, shared components, and the ViewModel layer |
| `service` | Android Library | The foreground service `SpoofingService`, the floating joystick `FloatingJoystickService`, boot-completed receivers, and other background/service-layer code |
| `xposed` | Android Library | The LSPosed/Xposed injection module itself: the `LocationHooker` entry point plus the system service hooks under `hooks/` (location, Wi-Fi, cell, Bluetooth, AppOps) and vendor adaptations under `hooks/vendor/` |
| `core-data` | Android Library | The data/domain layer shared by `app`, `app-ui`, and `service`: the Room database, repositories, and core utilities such as `ConfigManager`, `RootManager`, `EnvironmentScanner` |
| `core-geo` | Pure Kotlin/JVM | The only module in the project with no Android dependency: coordinate conversion, route geometry and the motion realism engine, plus the system detection rules shared by the hooks and the app (`vendor/`) |

The app delivers JSON through libxposed remote configuration; hooks read an in-memory snapshot:

```
┌─────────────────────────────────────────────────────────────────────────┐
│       LocationSpoofer Host Process (app/app-ui/service/core-data)       │
│  ┌─────────────────────────┐  ┌──────────────────────────────────────┐  │
│  │     Triple Map Engine   │  │          RouteStateMachine           │  │
│  │ (AMap / Baidu / Google) │  │     (IDLE / READY / RUN / PAUSE)     │  │
│  └────────────┬────────────┘  └──────────────────┬───────────────────┘  │
│               │                                  │                      │
│  ┌────────────▼──────────────────────────────────▼───────────────────┐  │
│  │                     ConfigManager (core-data)                     │  │
│  │     Serializes config + coord mappings, publishes via framework      │  │
│  └──────────────────────────────────┬────────────────────────────────┘  │
│  ┌──────────────────────────────────▼────────────────────────────────┐  │
│  │                     SpoofingService (service)                     │  │
│  │ Foreground service, floating joystick controller, gait/route calc │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────┬───────────────────────────────────┘
                                      │ (ConfigManager publishes remote preferences / remote files)
                                      ▼
              ┌─────────────────────────────────────────────────┐
              │ Framework-managed remote preferences / files │
              └────────────────────────┬────────────────────────┘
                                      │ (Framework notification -> background decode -> memory snapshot)
                                      ▼ LSPosed / libxposed (API 101+) Injection
┌─────────────────────────────────────────────────────────────────────────┐
│      System Processes (system_server / com.android.phone / Bluetooth)   │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                          LocationHooker                           │  │
│  │  - Location / GNSS / NMEA: SystemLocationServiceHooker            │  │
│  │  - Wi-Fi / cell / Bluetooth: System*ServiceHooker + hooks/network/│  │
│  │  - AppOps & mock settings: SystemAppOpsHooker                     │  │
│  │  - Vendor differences: hooks/vendor/ (HyperOS/ColorOS/OneUI/AOSP) │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

> [!NOTE]
> **IPC Design Decision**:
> `ConfigManager` publishes complete JSON snapshots with `XposedService.getRemotePreferences()`. UTF-8 payloads larger than 128 KiB are written to immutable framework-managed remote files before their pointers are atomically published. No SELinux patches or public temporary configuration files are needed.
> Hooks load the initial snapshot during module initialization and subscribe to preference changes. Background decoding and normalization replace the memory cache; hook calls only read that cache. A background check of the preference snapshot retries early initialization failures every second. Motion delivery and service discovery retain a separate timer.
> Private app preferences retain the latest desired state. Failed publications retry automatically, and framework reconnection republishes that state, including the latest stop or pause. Successful framework publication triggers cleanup of legacy config files, probes, and scripts. The framework persists its own configuration; this is not an entirely disk-free design.
> The framework must implement remote configuration. After upgrading, enable the module, reboot, and start a simulation. Hook Status should show `libxposed:remote-preferences` or `libxposed:remote-file` with matching publication times across system processes. This transport migration still requires device verification.

### System Adaptation

The global scheme hooks system services directly, and their internals differ between vendors and system versions, so each system needs to be adapted:

* [ADAPTATION_PROGRESS.md](ADAPTATION_PROGRESS.md): real-device verification status for each system and device under both schemes (the in-app "System Adaptation → Full adaptation progress" page shows this file);
* [vendor/README.md](xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/vendor/README.md): the adaptation framework and the **complete checklist for adding a new system**;
* [xposed/ADAPTATION_GUIDE.md](xposed/ADAPTATION_GUIDE.md): how to locate internal system classes and methods on a real device, and how to troubleshoot with the in-app "Hook Status" page.

---

## Requirements

* **OS Version**: Android 8.0 (API 26) or higher.
* **Root Manager**: Root access is required (recommended: [**KernelSU**](https://kernelsu.org) / **APatch** / **Magisk**).
* **Xposed Framework**: Installed and enabled **LSPosed (API 101+)** or compatible libxposed API 101+ environment.

---

## Quick Start

### 1. Build & Install

```bash
# 1. Clone the repository
git clone https://github.com/amahteru/location-spoofer.git

# 2. Build and install Debug APK
./gradlew installDebug
```

### 2. Activation
1. Open **KernelSU / APatch / Magisk** and grant Root permissions to LocationSpoofer.
2. Open **LSPosed Manager**, find **LocationSpoofer**, and **enable it**.
3. The module scope is fixed to **System Framework**, **Phone Services** and **Bluetooth** (pre-selected on install; keep them checked), then **reboot** so the system services load the hooks.
4. In the app's "System-Level Simulation Apps", turn on global mode or pick only the apps to spoof — there is no need to check target apps in LSPosed.

### 3. Usage Scenarios

#### Fixed-Point Simulation & Environment Locking
1. Launch LocationSpoofer, pick a target location on the map.
2. In the bottom drawer, enable spoofing toggles: **Mock Wi-Fi**, **Mock Cell Tower**, **Mock Bluetooth**, and **Enable Jitter**.
3. Tap "Start Simulation" to take over system GPS feeds.
4. To lock to a specific physical radio snapshot, tap the snapshot in "Manage Data".

#### Route Simulation
1. Switch to "Route Planning", mark waypoints on the road.
2. Select navigation mode (Loop / Round-trip / One-way) and speed class.
3. Turn on **"Use Real Route Planning"** (snaps to physical streets and inserts traffic light stops).
4. Tap "Start Simulation". You can toggle the floating joystick to fine-tune coordinates on-the-fly.

#### 🕵️‍♂️ Street Scanning & Custom Radio Fingerprints
1. Toggle **"Environment Map & Street Scan"** in Settings. Walk outdoors while the app silently records Wi-Fi, cell towers, and BLE signals.
2. Open "Manage Collected Data" to review, edit, or **manually customize** Wi-Fi (SSID, BSSID, RSSI) or cell records.
3. Export collected fingerprints as **JSON files** for backup or sharing.

---

## 🛠️ Tech Stack

* **Language**: 100% Kotlin
* **Module layout**: 6 Gradle modules — `app` / `app-ui` / `service` / `xposed` / `core-data` / `core-geo` (see [System Architecture](#system-architecture))
* **UI**: Jetpack Compose & Material Design 3, with dynamic color (Material You) on Android 12+ and an MD3 scheme generated from the brand color on older versions
* **Dependency Injection**: Koin, split per module into `coreDataModule` / `serviceModule` / `viewModelModule`, aggregated by `appModules` in the `app` module
* **Local Storage**: Room Database (SQLite) + Spatial Indexing (in `core-data`)
* **Networking & Serialization**: OkHttp 3 + Kotlinx Serialization
* **Map SDKs**: AMap 3DMap SDK / BaiduMap SDK / Google Maps & Places SDK
* **Xposed Hooking**: LSPosed API 101+ / libxposed (Service mode); hook implementations live under `xposed`'s `hooks/` and `hooks/network/` packages

---

## ⚠️ Disclaimer

This program is intended **solely for educational, academic, and developer testing purposes** (such as debugging coordinate-dependent apps).
Do not use this tool for any illegal activities or violations of third-party agreements (including fake attendance checks, exam cheating, commercial fraud, etc.).
The author is not responsible for any banned accounts, data losses, legal issues, or other direct/indirect damages arising from the use of this software.

---

## 📜 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

```
Copyright (C) 2026 SuseOAA
```
