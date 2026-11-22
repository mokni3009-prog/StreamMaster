// Top-level build file — Stream Master (Android TV, minSdk 21).
// Plugin versions live in gradle/libs.versions.toml (CONTRACT §2).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
}
