# Adaptation progress

This file records **on-device verification** of each system / device under the two simulation schemes. It is the single source of truth for “how far adaptation has come”.
Only register results actually tested on real hardware. Anything not tested is marked “unverified”; never fill it in by guesswork.

- See the release notes for the difference between the two schemes: `scoped` = per-app (target apps in the hook scope), `global` = global (hooks system processes).
- The global scheme uses a different adapter per system. See [vendor/README.md](xposed/src/main/java/com/vincenthzr/locationspoofer/xposed/hooks/vendor/README.md) for the architecture,
  and [ADAPTATION_GUIDE.md](xposed/ADAPTATION_GUIDE.md) for how to locate system interfaces.

- When testing the global scheme, the app’s “System adaptation → Hook status” page lists whether each component’s classes were found and methods hooked; use it as evidence when registering results.
- The system names in the first column of the “By system” table are used by the app to look up “this device’s status” (keywords are in `RomFamily.progressKeywords` in `core-geo`). Keep them when renaming.
- This is the English edition of [ADAPTATION_PROGRESS.md](ADAPTATION_PROGRESS.md). Update both files together.

**Status legend**: ✅ Verified · ⚠️ Partially working (see notes) · ❌ Not working · ❔ Unverified

---

## Global scheme (global)

### By system

| System | Adapter | Status | Verified system versions | Notes |
|---|---|---|---|---|
| Xiaomi HyperOS / MIUI | `HyperOsVendor` | ✅ | HyperOS 4, HyperOS 3 | Fully tested on HyperOS 4. On HyperOS 3 (REDMI K90 Ultra / OS3.0.308.0.WHPCNXM), global location injection works; other components not re-verified one by one. See [#74](https://github.com/HuangZhuoRui/LocationSpoofer/issues/74) |
| OPPO ColorOS / OnePlus OxygenOS | `ColorOs16Vendor` (API 36) / `ColorOsVendor` | ⚠️ | ColorOS 16 / Android 16 | Partially tested on PJX110. Telephony and Bluetooth pass; continuous location and fitness apps still need retesting. OxygenOS unverified. See [scope and screenshots](docs/adaptation/coloros16.md) |
| Samsung One UI | `OneUiVendor` | ❔ | — | Same as above |
| vivo OriginOS, HONOR MagicOS, Meizu Flyme, etc. | None (falls back to `AospVendor`) | ❔ | — | No dedicated adapter yet |
| Stock AOSP / AOSP-based | `AospVendor` | ❔ | — | Baseline adapter, no on-device verification yet |

### By component (HyperOS 4)

| Component | Process | Status | Notes |
|---|---|---|---|
| Location (GPS / network / passive provider) | system_server | ✅ | |
| Wi-Fi scan results and connection info | system_server | ✅ | |
| Network state sanitizing (NetworkCapabilities) | system_server | ✅ | |
| AppOps mock location check bypass | system_server | ✅ | |
| Cell info | com.android.phone | ✅ | |
| Bluetooth scanning | com.android.bluetooth | ❔ | Android 17 moved the scan entry to `le_scan.ScanBinder.registerAndStartScan`; adapted and confirmed hooked (Hook status page). Actual dispatch of virtual beacons not yet tested |

### By component (ColorOS 16, PJX110)

The results below come from the full build installed before the split. The review APK without native sensors and with the revised config fallback has been built but not yet re-verified end to end after a reboot.

| Component | Status | Scope verified |
|---|---|---|
| Hook status report | ✅ | All three reports (system_server, telephony, Bluetooth) readable, hooks attached without errors |
| Config sync | ✅ | All five configs consistent in the pre-split build; 6 additional isolated tests for the new failure paths |
| Location | ⚠️ | Early cached queries pass; continuous location and the 运动世界校园 crash retest not finished |
| Wi-Fi | ⚠️ | Early scan and connection info pass; not retested on the final build |
| Cell | ✅ | Cached queries, async requests, listeners; target and control apps isolated |
| Bluetooth | ✅ | Regular / PendingIntent, address filters, batching, active refresh, found / lost, target switching |
| Native sensors | ❔ | Not included in this review branch; reviewed and verified on a separate branch |

### Test devices

| Device | System version | Verified | Notes |
|---|---|---|---|
| Xiaomi 17 Pro Max | HyperOS 4 | 2026-09 | Development and verification device for the global scheme |
| REDMI K90 Ultra (M332BF / warsaw) | HyperOS 3 / OS3.0.308.0.WHPCNXM / Android 16 | 2026-09 | KernelSU (ksud 4.2.0) + LSPosed v2.2.0 (7854), global v3.0.0-beta-2 (arm64-v8a). Location injection works (`getLastLocation` provider=fused/network); other components not re-verified one by one. See [#74](https://github.com/HuangZhuoRui/LocationSpoofer/issues/74) |
| PJX110 | ColorOS 16 / PJX110_16.0.1.301 / Android 16 | 2026-09 | LSPosed IT 2.1.1 (7846), limited to the partial verification above |

---

## Per-app scheme (scoped)

| Device | System version | Root / LSPosed | Verified | Status |
|---|---|---|---|---|
| Xiaomi Mi 10 Pro | Android 15 | SukiSU + LSPosed (API 101+) | AMap location SDK callbacks (`com.bxkj.student` running flow), see PR #69 | ✅ |

> The per-app scheme is the project’s original scheme. Devices verified in the past haven’t been collected here yet; contributions welcome.

---

## Target app compatibility

| App | Global scheme | Per-app scheme | Notes |
|---|---|---|---|
| AMap | ✅ | ❔ | |
| Baidu Maps | ✅ | ❔ | |
| WeChat | ✅ | ❔ | |
| TIM | ✅ | ❔ | |
| Taobao / Xianyu | ⚠️ | ❔ | Location is simulated correctly, but Taobao still reports “location failed” when sending a location. Possibly a server-side IP region check; to be confirmed |

---

## How to update this file

After an on-device verification, add or edit a row in the matching table, stating at least:

1. **Device and system version**: use the real version number, which you can get with the commands below (Xiaomi example):
   ```bash
   adb shell getprop ro.mi.os.version.name
   ```
   ```bash
   adb shell getprop ro.build.version.release
   ```
2. **Root solution and LSPosed version**: confirm the framework supports libxposed remote config, and record the actual versions so connection and notification issues can be reproduced.
3. **Components / apps verified**: keep untested items at ❔. Don’t mark ✅ just because it “should work”.
4. **Verification date**: month precision is enough.
5. **Problems and symptoms**: when marking ⚠️ or ❌, describe the symptom, ideally with key logs or an issue link.

When adding a vendor or system-version adapter (under `vendor/profiles/`), also add a row under “Global scheme → By system”.
