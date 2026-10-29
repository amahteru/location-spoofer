# 构建与签名

本项目通过 `amahteru/location-spoofer` 的 GitHub Actions 构建安装包。

## 持久发布签名

本地发布密钥保存在 `.signing/locationspoofer-release.jks`，签名配置保存在项目根目录的 `keystore.properties`。两者都已被 Git 忽略，不会随代码推送。请自行安全备份密钥、别名和密码；重新克隆仓库无法恢复这些文件。

后续版本必须继续使用同一发布密钥，以保持覆盖升级能力。本地签名配置和云端签名 Secrets 应对应同一份密钥，不要每次发布重新生成。

在仓库中设置以下 Repository Secrets：

| Secret 名称 | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 持久发布密钥文件的 Base64 内容 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 签名密钥别名 |
| `KEY_PASSWORD` | 签名密钥密码 |
| `AMAP_API_KEY` | 高德 Android Key |
| `BAIDU_API_KEY` | 百度 Android SDK 的 AK |
| `BAIDU_DARK_STYLE_ID` | 百度深色地图样式 ID（可选） |
| `GOOGLE_MAPS_API_KEY` | Google Maps Android Key（可选） |

## 地图 Key

地图 Key 不写在仓库里，构建时依次从同名环境变量（CI 中即上面的 Secrets）和项目根目录的 `local.yml` 读取，本地开发复制 `local.yml.example` 为 `local.yml` 后填写即可。未配置的 Key 会在构建日志里给出警告，对应地图无法使用。

高德、百度的 Key 在各自开放平台申请 Android 平台的 Key，包名填 `io.github.amahteru.locationspoofer`，发布版 SHA1 填发布密钥的指纹，调试版 SHA1 填本机 debug keystore 的指纹。换发布密钥或包名后要同步更新 Key 的绑定。

## CI 构建

推送到 `main`、提交 PR 或在 Actions 页面手动触发时，`Build` 工作流会运行单元测试，构建两个方案的 Release APK，并作为构建产物 `LocationSpoofer-apk` 上传：

```text
LocationSpoofer-scoped-arm64-v8a-v<版本名>.apk
LocationSpoofer-global-arm64-v8a-v<版本名>.apk
```

未配置签名 Secrets 时 APK 不签名，文件名带 `-unsigned`，无法安装；未配置地图 Key 时构建日志会给出警告。工作流不再创建 Tag 和 Release。

## 首次切换签名

本仓库的包名为 `io.github.amahteru.locationspoofer`，并使用自己的独立发布签名。原作者版本（包名 `com.vincenthzr.locationspoofer` 等）会被系统当作另一个应用；此前使用调试签名的 Debug 包，如果签名与本仓库发布包不同，也不能直接覆盖安装。

切换前先在旧版导出收藏位置、路线和采集数据等备份，在 LSPosed 中停用旧模块，卸载旧包后安装本仓库的 APK，再启用模块、设置作用域、重启设备并导入备份。完成首次迁移后，后续版本保持同一包名和签名即可继续覆盖升级。
