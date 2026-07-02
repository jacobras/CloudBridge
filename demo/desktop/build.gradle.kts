@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    sourceSets {
        named("desktopMain") {
            dependencies {
                implementation(projects.demo.shared)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

buildConfig {
    packageName("nl.jacobras.cloudbridge.demo")
    buildConfigField<String>(
        "DROPBOX_CLIENT_ID",
        localProps.getProperty("dropboxClientId") ?: ""
    )
    buildConfigField<String>(
        "GOOGLE_DRIVE_CLIENT_ID",
        localProps.getProperty("googleDriveDesktopClientId") ?: ""
    )
    buildConfigField<String>(
        "GOOGLE_DRIVE_CLIENT_SECRET",
        localProps.getProperty("googleDriveDesktopSecret") ?: ""
    )
    buildConfigField<String>(
        "ONEDRIVE_CLIENT_ID",
        localProps.getProperty("onedriveClientId") ?: ""
    )
}

compose.desktop {
    application {
        mainClass = "nl.jacobras.cloudbridge.demo.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "nl.jacobras.cloudbridge.demo"
            packageVersion = "1.0.0"
        }
    }
}