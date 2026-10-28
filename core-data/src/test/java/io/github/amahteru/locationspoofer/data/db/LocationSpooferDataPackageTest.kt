package io.github.amahteru.locationspoofer.data.db

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSpooferDataPackageTest {
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val importJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun `未勾选密钥时导出的 JSON 不包含任何密钥内容`() {
        val pkg = LocationSpooferDataPackage(
            savedLocations = listOf(
                io.github.amahteru.locationspoofer.data.model.SavedLocation("家", 31.0, 121.0)
            ),
            settings = ExportedSettings(altitude = "25.0", satelliteCount = "20"),
            apiKeys = null
        )

        val text = exportJson.encodeToString(pkg)

        assertTrue(text.contains("\"apiKeys\": null"))
        listOf(
            "amapApiKey", "baiduApiKey", "googleApiKey",
            "wigleApiToken", "opencellidApiToken"
        ).forEach { field ->
            assertFalse("导出文件里不应出现密钥字段 $field", text.contains(field))
        }
    }

    @Test
    fun `勾选密钥时导出的 JSON 才会带上密钥`() {
        val pkg = LocationSpooferDataPackage(
            apiKeys = ExportedApiKeys(amapApiKey = "test-amap-key")
        )
        val text = exportJson.encodeToString(pkg)
        assertTrue(text.contains("amapApiKey"))
        assertTrue(text.contains("test-amap-key"))
    }

    @Test
    fun `旧的 version 2 文件仍然可以解析，settings 与 apiKeys 落到 null`() {
        val legacyV2 = """
            {
              "version": 2,
              "exportTimestamp": 1700000000000,
              "appVersion": "2.0.0",
              "locations": [],
              "savedLocations": [
                {"name": "公司", "lat": 31.5, "lng": 121.5}
              ],
              "savedRoutes": [],
              "appCoordinateSystems": {"com.tencent.mm": "GCJ-02"},
              "unknownFutureField": true
            }
        """.trimIndent()

        val pkg = importJson.decodeFromString<LocationSpooferDataPackage>(legacyV2)

        assertEquals(2, pkg.version)
        assertEquals(1, pkg.savedLocations.size)
        assertEquals("公司", pkg.savedLocations[0].name)
        assertNull("旧文件没有这两个分类，应当为 null", pkg.settings)
        assertNull("旧文件没有这两个分类，应当为 null", pkg.apiKeys)
    }

    @Test
    fun `新格式可以完整往返序列化`() {
        val original = LocationSpooferDataPackage(
            savedLocations = listOf(
                io.github.amahteru.locationspoofer.data.model.SavedLocation("家", 31.0, 121.0)
            ),
            settings = ExportedSettings(
                mockWifi = false,
                mockCell = true,
                mockBluetooth = false,
                enableJitter = false,
                altitude = "12.5",
                satelliteCount = "18",
                mapType = "SATELLITE",
                mapEngine = "AMAP"
            ),
            apiKeys = ExportedApiKeys(baiduApiKey = "bd-key")
        )

        val restored = importJson.decodeFromString<LocationSpooferDataPackage>(
            exportJson.encodeToString(original)
        )

        assertEquals(4, restored.version)
        assertEquals(original.settings, restored.settings)
        assertEquals(original.apiKeys, restored.apiKeys)
        assertEquals(original.savedLocations, restored.savedLocations)
    }
}
