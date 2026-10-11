buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // Match the complete Kotlin toolchain already installed for this preview AGP.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.5.0-Beta1")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.5.0-Beta1")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
}
