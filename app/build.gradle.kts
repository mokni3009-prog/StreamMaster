// Stream Master — app module (Android TV, CONTRACT §2).
// Versions come from gradle/libs.versions.toml; do not hard-code them here.
import java.net.HttpURLConnection
import java.net.URL

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
        versionName = "1.2.2"
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
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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

// ---------------------------------------------------------------------------
// Build-error reporter (§4.66b) — Android Studio build failures, sent to the
// Stream Master crash-report server (Cloudflare Worker + D1) so the coach no
// longer has to send screenshots of the Build Output.
// How it works: a Gradle BuildListener runs when ANY Gradle invocation on his
// PC finishes (Build APK, Sync, ...). On success it does nothing. On failure
// it POSTs one JSON report to the same Worker endpoint the in-app crash
// reporter uses (error_type "BuildError"). The report holds the failed task,
// the failure message and the cause chain — enough to know what broke and
// where. It does NOT hold the full compiler error list: Gradle does not
// expose the Build Output text to a build script, so for a generic Kotlin
// "Compilation error" the screenshot is still needed.
// Safety: JDK HttpURLConnection only, no new dependency, 5s timeouts, every
// failure swallowed — a report that fails can never fail the build.
// ---------------------------------------------------------------------------
val buildReportVersionName: String = android.defaultConfig.versionName ?: "?"
val buildReportVersionCode: Int = android.defaultConfig.versionCode ?: 0
val buildReportGradleVersion: String = gradle.gradleVersion

fun jsonEscapeBuildReport(value: String): String {
    val out = StringBuilder()
    for (ch in value) {
        when (ch) {
            '\\' -> out.append("\\\\")
            '"' -> out.append("\\\"")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (ch < ' ') out.append(String.format("\\u%04x", ch.code)) else out.append(ch)
        }
    }
    return out.toString()
}

fun postBuildReport(url: String, body: String) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { stream -> stream.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        if (code !in 200..299) {
            try { connection.errorStream?.close() } catch (_: Exception) { }
        }
    } finally {
        connection.disconnect()
    }
}

gradle.addListener(object : org.gradle.BuildAdapter() {
    override fun buildFinished(result: org.gradle.BuildResult) {
        val failure = result.failure ?: return
        try {
            val chain = StringBuilder()
            var cause: Throwable? = failure
            var depth = 0
            while (cause != null && depth < 8) {
                chain.append(cause.javaClass.simpleName).append(": ")
                    .append(cause.message ?: "").append("\n")
                cause = cause.cause
                depth++
            }
            val body = """{"app_version":"${jsonEscapeBuildReport(buildReportVersionName)}","version_code":$buildReportVersionCode,"device_model":"AndroidStudio-PC","android_version":"${jsonEscapeBuildReport("Gradle " + buildReportGradleVersion)}","error_type":"BuildError","message":"${jsonEscapeBuildReport(("Build failed: " + (failure.message ?: "unknown error")).take(1000))}","stack_trace":"${jsonEscapeBuildReport(chain.toString().take(8000))}"}"""
            postBuildReport(
                "https://streammaster-crash-reporter.streammaster.workers.dev",
                body
            )
        } catch (_: Exception) {
            // A failed report must never fail the build.
        }
    }
})
