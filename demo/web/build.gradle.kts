@file:Suppress("OPT_IN_USAGE")

import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

kotlin {
    js {
        outputModuleName = "demo"
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }
    wasmJs {
        outputModuleName = "demo"
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        webMain.dependencies {
            implementation(projects.demo.shared)
            implementation(libs.kermit)
            implementation(libs.kotlinx.browser)
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
        localProps.getProperty("googleDriveWebClientId") ?: ""
    )
    buildConfigField<String>(
        "ONEDRIVE_CLIENT_ID",
        localProps.getProperty("onedriveClientId") ?: ""
    )
}