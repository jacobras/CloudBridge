@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.buildconfig)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

dependencies {
    implementation(projects.demo.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
}

buildConfig {
    packageName("nl.jacobras.cloudbridge.demo")
    buildConfigField<String>("DRIVE_DESKTOP_SECRET", localProps.getProperty("driveDesktopSecret") ?: "")
}

android {
    namespace = "nl.jacobras.cloudbridge.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "nl.jacobras.cloudbridge.demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = project.version.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}