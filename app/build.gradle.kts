plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(from = rootProject.file("gradle/versioning.gradle.kts"))
val gitVersionCode = extra["androidVersionCode"] as Int
val gitVersionName = extra["androidVersionName"] as String

android {
    namespace = "cc.samlab.ticket"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "cc.samlab.ticket"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
}
