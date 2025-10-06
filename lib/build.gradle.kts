@file:Suppress("OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kotlin.serialization)
}

group = "nl.jacobras"
version = "0.0.1"

kotlin {
    js { browser() }
    wasmJs {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.crypto.sha2)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktorfit)
            implementation(libs.multiplatform.settings)
            implementation(libs.urlencoder)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.assertK)
        }
    }
}