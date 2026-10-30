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
    testBuildType = providers.gradleProperty("crticket.testBuildType").getOrElse("debug")
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    defaultConfig {
        applicationId = "cc.samlab.ticket"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName
        buildConfigField("String", "STATION_DATA_VERSION", "\"$stationDataVersion\"")
        buildConfigField("boolean", "UPDATE_ENABLED", "true")
        buildConfigField("String", "UPDATE_TEST_TAG", "\"\"")
        manifestPlaceholders["applicationLabel"] = "@string/home_title"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets.getByName("main").assets.directories.add(layout.buildDirectory.dir("welcome-assets").get().asFile.path)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    buildTypes {
        create("ci") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".ci"
            versionNameSuffix = "-ci"
            matchingFallbacks += "debug"
            buildConfigField("boolean", "UPDATE_ENABLED", "false")
            manifestPlaceholders["applicationLabel"] = "CRTicket CI"
        }
        create("updateTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".updatetest"
            versionNameSuffix = "-update-test"
            matchingFallbacks += "debug"
            buildConfigField("String", "UPDATE_TEST_TAG", "\"v0.9.4\"")
            manifestPlaceholders["applicationLabel"] = "CRTicket TEST"
        }
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
    listOf("ci", "updateTest").forEach { variant ->
        sourceSets.getByName(variant) {
            kotlin.directories.add(file("src/debug/java").path)
            manifest.srcFile("src/debug/AndroidManifest.xml")
        }
    }
}

dependencies {
    implementation(libs.konfetti.compose)
    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.strikethrough)
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

val packageChangelogs = tasks.register<Sync>("packageChangelogs") {
    from(rootProject.file("changelogs")) {
        include("*.md")
        exclude("README.md")
        into("changelogs")
    }
    into(layout.buildDirectory.dir("welcome-assets"))
}
tasks.named("preBuild") { dependsOn(packageChangelogs) }
