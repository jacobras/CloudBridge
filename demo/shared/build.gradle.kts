@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
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

    sourceSets {
        commonMain.dependencies {
            api(projects.library)
            api(libs.androidx.viewmodel)
            api(libs.compose.adaptive)
            api(libs.compose.adaptive.layout)
            api(libs.compose.adaptive.navigation)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.kotlin.coroutines.core)
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