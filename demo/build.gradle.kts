@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")
    js {
        browser()
        binaries.executable()
    }
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.viewmodel)
            implementation(libs.compose.foundation)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.humanReadable)
            implementation(libs.kermit)
            implementation(libs.multiplatform.settings)

            implementation(projects.library)
        }
        named("desktopMain") {
            buildConfig {
                packageName("nl.jacobras.cloudbridge.demo")
                buildConfigField<String>("DRIVE_DESKTOP_SECRET", localProps.getProperty("driveDesktopSecret") ?: "")
            }
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        androidMain {
            buildConfig {
                packageName("nl.jacobras.cloudbridge.demo")
                buildConfigField<String>("DRIVE_DESKTOP_SECRET", localProps.getProperty("driveDesktopSecret") ?: "")
            }
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.multiplatform.settings.no.arg)
                implementation(libs.androidx.browser)
            }
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

android {
    namespace = "nl.jacobras.cloudbridge.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "nl.jacobras.cloudbridge.demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = project.version.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "nl.jacobras.cloudbridge.demo.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "nl.jacobras.cloudbridge.demo"
            packageVersion = "1.0.0"
        }
    }
}