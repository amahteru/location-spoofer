import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.vincenthzr.locationspoofer.xposed"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        // 本模块只在 system_server / com.android.phone / com.android.bluetooth 里运行，Hook 系统内部实现
        // 本来就要反射隐藏 API；平台进程不受隐藏 API 限制，这几项检查针对的是普通 App，这里统一关闭
        disable += setOf("PrivateApi", "DiscouragedPrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-geo"))
    compileOnly(libs.xposed.api)
    compileOnly(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
