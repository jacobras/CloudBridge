@file:Suppress("OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "nl.jacobras"
version = "0.0.1"

kotlin {
    js { browser() }
    wasmJs { browser() }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktorfit)
        }
    }
}