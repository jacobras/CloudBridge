@file:Suppress("OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    js {
        outputModuleName = "demo"
        browser()
        binaries.executable()
    }
    wasmJs {
        outputModuleName = "demo"
        browser()
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