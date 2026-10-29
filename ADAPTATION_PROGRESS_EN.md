# Adaptation progress

On-device verification of the global scheme on each system. The app's "System adaptation" page reads this file. Only record results tested on real devices; mark anything untested as ❔.

**Status**: ✅ Verified · ⚠️ Partially working · ❌ Not working · ❔ Unverified

## Global scheme

### By system

| System | Adapter | Status | Verified versions | Notes |
|---|---|---|---|---|
| Xiaomi HyperOS / MIUI | `HyperOsVendor` | ✅ | HyperOS 4, HyperOS 3 | Fully tested on HyperOS 4; only location verified on HyperOS 3 |
| OPPO ColorOS / OnePlus OxygenOS | `ColorOs16Vendor` / `ColorOsVendor` | ⚠️ | ColorOS 16 | Telephony and Bluetooth pass; continuous location and Wi-Fi need retesting; OxygenOS unverified |
| Samsung One UI | `OneUiVendor` | ❔ | — | |
| vivo OriginOS, HONOR MagicOS, Meizu Flyme, etc. | `AospVendor` | ❔ | — | No dedicated adapter |
| Stock AOSP / AOSP-based | `AospVendor` | ❔ | — | |

### By component (HyperOS 4)

| Component | Process | Status | Notes |
|---|---|---|---|
| Location | system_server | ✅ | |
| Wi-Fi scan and connection info | system_server | ✅ | |
| Network state | system_server | ✅ | |
| AppOps mock location check | system_server | ✅ | |
| Cell | com.android.phone | ✅ | |
| Bluetooth scanning | com.android.bluetooth | ❔ | Hooks attached; virtual beacon dispatch not tested |

### Test devices

| Device | System version | Date |
|---|---|---|
| Xiaomi 17 Pro Max | HyperOS 4 | 2026-09 |
| REDMI K90 Ultra | HyperOS 3 / Android 16 | 2026-09 |
| OPPO PJX110 | ColorOS 16 / Android 16 | 2026-09 |

## Target apps

| App | Status | Notes |
|---|---|---|
| AMap | ✅ | |
| Baidu Maps | ✅ | |
| WeChat | ✅ | |
| TIM | ✅ | |
| Taobao / Xianyu | ⚠️ | Location is correct, but Taobao reports "location failed" when sending a location; possibly a server-side IP check |
