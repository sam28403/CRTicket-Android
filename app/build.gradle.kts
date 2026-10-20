plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

apply(from = rootProject.file("gradle/versioning.gradle.kts"))
apply(from = rootProject.file("gradle/web-assets.gradle.kts"))
val gitVersionCode = extra["androidVersionCode"] as Int
val gitVersionName = extra["androidVersionName"] as String
val stationDataVersion = Regex("Station Version (\\d+)")
    .find(rootProject.file("web/src/views/MainView.vue").readText())
    ?.groupValues?.get(1) ?: error("Missing bundled station data version")

android {
    namespace = "cc.samlab.ticket"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "cc.samlab.ticket"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName
        buildConfigField("String", "STATION_DATA_VERSION", "\"$stationDataVersion\"")
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
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += "release"
        }
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.fragment) {
            because("Play services brings Fragment 1.1.0; Activity Result requires 1.3.0 or newer")
        }
    }
    implementation(libs.kotlin.stdlib)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.window)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.android.fastscroll)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.window.testing)
}
