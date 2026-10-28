import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vincenthzr.locationspoofer.xposed"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
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
