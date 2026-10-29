import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "io.github.amahteru.locationspoofer.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 33

        buildConfigField(
            "String",
            "VERSION_NAME",
            "\"${providers.gradleProperty("APP_VERSION_NAME").get()}\""
        )

        vectorDrawables {
            useSupportLibrary = true
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
}

abstract class BundleAdaptationProgress : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        sources.files.forEach { src ->
            src.copyTo(outputDir.get().asFile.resolve(src.name), overwrite = true)
        }
    }
}

val bundleAdaptationProgress = tasks.register<BundleAdaptationProgress>("bundleAdaptationProgress") {
    sources.from(
        rootProject.layout.projectDirectory.file("ADAPTATION_PROGRESS.md"),
        rootProject.layout.projectDirectory.file("ADAPTATION_PROGRESS_EN.md")
    )
}

androidComponents {
    onVariants { variant ->
        if (variant.flavorName == "global") {
            variant.sources.assets?.addGeneratedSourceDirectory(bundleAdaptationProgress, BundleAdaptationProgress::outputDir)
        }
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
