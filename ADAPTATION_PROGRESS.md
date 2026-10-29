# 适配进度

全局方案在各系统上的实机验证结果，App 的“系统适配”页会读取本文件。只登记真机实测结果，没测过的写 ❔。

**状态**：✅ 实测通过 · ⚠️ 部分可用 · ❌ 不可用 · ❔ 未验证

## 全局方案

### 按系统

| 系统 | 适配器 | 状态 | 已验证版本 | 备注 |
|---|---|---|---|---|
| 小米 HyperOS / MIUI | `HyperOsVendor` | ✅ | HyperOS 4、HyperOS 3 | HyperOS 4 完整实测；HyperOS 3 仅验证了定位 |
| OPPO ColorOS / 一加 OxygenOS | `ColorOs16Vendor` / `ColorOsVendor` | ⚠️ | ColorOS 16 | 电话、蓝牙通过；持续定位、Wi-Fi 待复测；OxygenOS 未验证 |
| 三星 One UI | `OneUiVendor` | ❔ | — | |
| vivo OriginOS、荣耀 MagicOS、魅族 Flyme 等 | `AospVendor` | ❔ | — | 无专用适配器 |
| 原生 AOSP / 类原生 | `AospVendor` | ❔ | — | |

### 按组件（HyperOS 4）

| 组件 | 进程 | 状态 | 备注 |
|---|---|---|---|
| 定位 | system_server | ✅ | |
| Wi-Fi 扫描与连接信息 | system_server | ✅ | |
| 网络状态 | system_server | ✅ | |
| AppOps 模拟定位检测 | system_server | ✅ | |
| 基站 | com.android.phone | ✅ | |
| 蓝牙扫描 | com.android.bluetooth | ❔ | Hook 已挂载，虚拟信标派发未实测 |

### 实测设备

| 设备 | 系统版本 | 日期 |
|---|---|---|
| 小米 17 Pro Max | HyperOS 4 | 2026-09 |
| REDMI K90 Ultra | HyperOS 3 / Android 16 | 2026-09 |
| OPPO PJX110 | ColorOS 16 / Android 16 | 2026-09 |

## 目标应用

| 应用 | 状态 | 备注 |
|---|---|---|
| 高德地图 | ✅ | |
| 百度地图 | ✅ | |
| 微信 | ✅ | |
| TIM | ✅ | |
| 淘宝 / 闲鱼 | ⚠️ | 位置正确，但淘宝发送位置时提示“定位失败”，疑为服务端 IP 校验 |
