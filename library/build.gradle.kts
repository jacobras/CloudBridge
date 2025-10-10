@file:Suppress("OPT_IN_USAGE")

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.kotlin.serialization)
    id("com.vanniktech.maven.publish") version "0.34.0"
    signing
}

group = "nl.jacobras"

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