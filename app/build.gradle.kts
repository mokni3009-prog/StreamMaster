// Stream Master — app module (Android TV, CONTRACT §2).
// Versions come from gradle/libs.versions.toml; do not hard-code them here.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.streammaster.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.streammaster.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 121
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // libVLC / OkHttp / Cronet ship duplicate META-INF files.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM first so all Compose artifacts share one version.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    // Compose for TV (Leanback-style focus, D-pad navigation).
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // Hilt (DI) — kapt per CONTRACT §2.
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Media3 (ExoPlayer) — primary engine on API >= 23.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.datasource.cronet)
    implementation(libs.media3.common)

    // libVLC — primary engine on API 21-22, automatic fallback on API >= 23.
    // Version note: see gradle/libs.versions.toml (3.0.24 is not published;
    // newest stable 3.x is used instead).
    implementation(libs.libvlc.all)

    // Room (local database) + DataStore (settings) + WorkManager (refresh).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.workmanager.ktx)

    // Network: Retrofit/OkHttp for the Xtream/Stalker APIs; Cronet for the
    // player stack (HttpEngine on API 34+, else Cronet, else OkHttp).
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.cronet.embedded)

    // Images (channel logos, posters) — Coil.
    implementation(libs.coil.compose)

    // Chromecast (§4.46) — Cast sender framework; see cast/CastManager.kt.
    implementation(libs.play.services.cast.framework)

    // Coroutines.
    implementation(libs.kotlinx.coroutines.android)
}
