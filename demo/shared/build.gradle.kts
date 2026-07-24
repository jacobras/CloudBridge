@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties


plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

kotlin {
    android {
        namespace = "nl.jacobras.cloudbridge.demo.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources { enable = true }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    js { browser() }
    wasmJs { browser() }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.library)
            api(libs.androidx.viewmodel)
            implementation(libs.compose.adaptive)
            implementation(libs.compose.adaptive.layout)
            implementation(libs.compose.adaptive.navigation)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlin.coroutines.core)
            implementation(libs.compose.icons.extended)
            implementation(libs.compose.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.humanReadable)
            implementation(libs.kermit)
            implementation(libs.multiplatform.settings)
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        androidMain.dependencies {
            implementation(libs.multiplatform.settings.no.arg)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        freeCompilerArgs.add("-Xexplicit-backing-fields")
    }
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

buildConfig {
    packageName("nl.jacobras.cloudbridge.demo.shared")
    buildConfigField<String>(
        "DROPBOX_CLIENT_ID",
        localProps.getProperty("dropboxClientId") ?: ""
    )
    buildConfigField<String>(
        "GOOGLE_DRIVE_CLIENT_ID",
        localProps.getProperty("googleDriveIosClientId") ?: ""
    )
    buildConfigField<String>(
        "ONEDRIVE_CLIENT_ID",
        localProps.getProperty("onedriveClientId") ?: ""
    )
}