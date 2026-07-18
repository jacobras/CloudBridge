@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    signing
}

group = "nl.jacobras"

kotlin {
    android {
        namespace = "nl.jacobras.cloudbridge"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    js { browser() }
    jvm("desktop") {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
    }
    iosArm64()
    iosSimulatorArm64()

    explicitApi()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.coroutines.core)
            implementation(libs.kotlin.crypto.sha2)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktorfit)
            implementation(libs.urlencoder)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.assertK)
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
            }
        }
        webMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.play.services.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.multiplatform.settings.no.arg)
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("nl.jacobras", "cloudbridge")

    pom {
        name.set("CloudBridge")
        description.set("Multiple clouds, one Kotlin Multiplatform bridge")
        url.set("https://github.com/jacobras/CloudBridge")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("jacobras")
                name.set("Jacob Ras")
                email.set("info@jacobras.nl")
            }
        }
        scm {
            url.set("https://github.com/jacobras/CloudBridge")
        }
    }
}

signing {
    setRequired {
        !gradle.taskGraph.allTasks.any { it is PublishToMavenLocal }
    }
}

tasks.named("sourcesJar") {
    dependsOn("kspCommonMainKotlinMetadata")
}