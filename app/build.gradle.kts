@file:Suppress("DEPRECATION")

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

android {
    namespace = "io.github.amahteru.locationspoofer"
    compileSdk = 37

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        disable += "ChromeOsAbiSupport"
        lintConfig = file("lint.xml")
    }

    fun getLocalConfig(key: String): String? {
        val localYml = file("../local.yml")
        if (localYml.exists()) {
            val line = localYml.readLines().find { it.startsWith("$key:") }
            if (line != null) {
                return line.substringAfter(":").trim().removeSurrounding("\"")
                    .removeSurrounding("'")
            }
        }
        return null
    }

    fun mapConfig(key: String): String {
        val value = System.getenv(key)?.takeIf { it.isNotBlank() } ?: getLocalConfig(key) ?: ""
        if (value.isBlank()) logger.warn("未配置 $key，对应地图功能将不可用（见 local.yml.example）")
        return value
    }

    val googleMapsApiKey = mapConfig("GOOGLE_MAPS_API_KEY")
    val amapApiKey = mapConfig("AMAP_API_KEY")
    val baiduApiKey = mapConfig("BAIDU_API_KEY")
    val baiduDarkStyleId = mapConfig("BAIDU_DARK_STYLE_ID")

    defaultConfig {
        applicationId = "io.github.amahteru.locationspoofer"
        testInstrumentationRunner = "io.github.amahteru.locationspoofer.SystemFilesInstrumentation"
        minSdk = 33
        targetSdk = 37
        versionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("APP_VERSION_NAME").get()

        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
        manifestPlaceholders["amapApiKey"] = amapApiKey
        manifestPlaceholders["baiduApiKey"] = baiduApiKey
        manifestPlaceholders["baiduDarkStyleId"] = baiduDarkStyleId

        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a")
                isUniversalApk = false
            }
        }
    }

    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)?.takeIf { it.isNotBlank() }

    val keystorePath = signingValue("KEYSTORE_FILE_PATH", "storeFile")
    val hasReleaseKeystore = keystorePath != null && file(keystorePath).exists()

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystorePath!!)
                storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
            }
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName(if (hasReleaseKeystore) "release" else "debug")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$googleMapsApiKey\"")
        }
    }
    flavorDimensions += "scheme"
    productFlavors {
        create("scoped") {
            dimension = "scheme"
            buildConfigField("boolean", "GLOBAL_SCHEME", "false")
        }
        create("global") {
            dimension = "scheme"
            buildConfigField("boolean", "GLOBAL_SCHEME", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xskip-metadata-version-check"
        )
    }
}

dependencies {
    implementation(project(":core-geo"))
    implementation(project(":core-data"))
    implementation(project(":service"))
    implementation(project(":app-ui"))
    implementation(project(":xposed"))

    implementation(libs.xposed.service)
    implementation(libs.koin.androidx.compose)
    implementation(libs.amap.map)
    implementation(libs.amap.search)
    implementation(libs.baidu.map)
    implementation(libs.google.places)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
}
