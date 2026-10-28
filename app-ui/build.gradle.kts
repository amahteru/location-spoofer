import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "com.vincenthzr.locationspoofer.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26

        buildConfigField(
            "String",
            "VERSION_NAME",
            "\"${providers.gradleProperty("APP_VERSION_NAME").get()}\""
        )

        vectorDrawables {
            useSupportLibrary = true
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xskip-metadata-version-check",
            "-opt-in=kotlinx.serialization.InternalSerializationApi"
        )
    }
}

dependencies {
    implementation(project(":core-data"))
    implementation(project(":core-geo"))

    implementation(libs.koin.androidx.compose)
    implementation(libs.amap.map)
    implementation(libs.amap.search)
    implementation(libs.baidu.map)
    implementation(libs.baidu.location)
    implementation(libs.baidu.search)
    implementation(libs.google.maps)
    implementation(libs.google.places)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    implementation(libs.okhttp)
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

    testImplementation(libs.junit)
}
