@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    jvm("desktop")

    sourceSets {
        named("desktopMain") {
            buildConfig {
                packageName("nl.jacobras.cloudbridge.demo")
                buildConfigField<String>("DRIVE_DESKTOP_SECRET", localProps.getProperty("driveDesktopSecret") ?: "")
            }
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