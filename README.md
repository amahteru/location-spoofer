# LocationSpoofer

**简体中文** | [English](README_EN.md)

基于 LSPosed 的 Android 虚拟定位模块，同时模拟周边的 Wi-Fi、基站、蓝牙、卫星与计步数据，让位置和环境保持一致。

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-purple.svg)](https://kotlinlang.org)
[![libxposed](https://img.shields.io/badge/libxposed-API%20100%2B-purple.svg)](https://github.com/libxposed/api)

## 功能

**定位**
- 定点模拟：在地图上选点或搜索地点后开始模拟。
- 路线模拟：多点路线，可选高德真实道路规划，并在部分路口停留 15 秒模拟红绿灯；支持“循环自动巡航”和“悬浮摇杆手动控制”两种模式。
- 速度档位：步行、跑步、骑行、驾车或自定义速度。
- 运动真实度：速度波动、海拔起伏、步频变化，可选轻微位置抖动。

**环境**
- Wi-Fi：扫描结果、当前连接信息、已保存网络、DHCP 信息。
- 基站：`CellInfo`、`CellLocation`、运营商、`ServiceState`、信号强度，以及 `PhoneStateListener` / `TelephonyCallback` 回调。
- 蓝牙：BLE 扫描结果。
- 卫星：`GnssStatus` 卫星列表、NMEA 语句，以及 `Location` 附带的卫星数。
- 计步：步数计数器和步数检测传感器。
- 数据来源：实地采集（点位或路线，无需 Root）、WiGLE 和 OpenCellID 导入；附近有多个采集点时按距离加权混合。

**伪装**
- 直接改写高德、百度、腾讯定位 SDK 的回调结果。
- 清除 `Location` 的模拟位置标记，拦截 AppOps 和 `Settings.Secure` 中的模拟位置查询。

**应用**
- 地图引擎：高德、百度、Google 地图，默认自动选择高德。自行构建时需要提供各地图的 API Key。
- 数据管理：查看、编辑和删除采集数据，支持 JSON 导入导出。
- Material Design 3 界面，跟随系统动态取色，支持简体中文和英文。

## 要求

- Android 13（API 33）及以上，arm64-v8a 设备
- 已 Root（KernelSU、APatch 或 Magisk）
- LSPosed，或其他支持 libxposed API 100+ 的框架

## 安装

从 [Releases](https://github.com/amahteru/location-spoofer/releases) 下载 `LocationSpoofer-scoped-arm64-v8a-*.apk` 安装，或自行构建：

```bash
git clone https://github.com/amahteru/location-spoofer.git
cd location-spoofer
./gradlew installDebug
```

构建需要 JDK 17 和 Android SDK（API 37）。地图 Key 不随仓库提供：复制 `local.yml.example` 为项目根目录的 `local.yml`，填入自己申请的高德、百度（以及可选的 Google）Key，也可以用同名环境变量提供。Windows 环境见 [docs/development-windows.md](docs/development-windows.md)。

## 使用

1. 在 Root 管理器中授予 LocationSpoofer Root 权限。
2. 在 LSPosed 中启用模块，在作用域里勾选要模拟的应用（如微信、钉钉、学习通、高德地图），然后强行停止这些应用或重启手机。
3. **定位**页：选好位置后点“定点模拟”，在“模拟选项”中勾选要模拟的 Wi-Fi、基站、蓝牙数据，点“开始模拟”。
4. **路线**页：依次添加路点，选择运行模式和速度，点“开始路线模拟”。
5. **功能**页：采集本地数据、管理采集数据、导入与导出数据。
6. **信息**页：地图配置、运动真实度、Root 方案、后台保活等设置。

开始模拟时默认会重启目标应用，让新配置立即生效；可在“模拟选项”中关闭。

## 结构

| 模块 | 说明 |
|---|---|
| `app` | 应用入口与打包配置，汇总各模块的依赖注入 |
| `app-ui` | Jetpack Compose 界面与 ViewModel |
| `service` | 前台服务、悬浮摇杆、开机恢复 |
| `xposed` | LSPosed 模块入口 `LocationHooker` 及各类 Hook |
| `core-data` | Room 数据库、Repository、配置发布、环境扫描 |
| `core-geo` | 纯 Kotlin 模块：坐标换算、路线几何、运动模型 |

应用通过 libxposed 的远程偏好把配置快照发布给目标应用进程，超过 128 KiB 时改用框架管理的远程文件。Hook 只读取内存中的配置缓存，不需要修改 SELinux 策略，也不写公共临时目录。

## 免责

本项目仅供学习研究和合法的开发测试使用。请勿用于违法违规或违反平台服务协议的用途（包括但不限于虚假打卡、考试作弊、商业欺诈）。因使用本项目造成的账号封禁、数据丢失、法律纠纷等后果由使用者自行承担。

## 协议

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。
