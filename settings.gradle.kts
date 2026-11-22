pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google Maven hosts AGP, Media3, tv-material/tv-foundation and
        // org.chromium.net:cronet-embedded (not on Maven Central).
        google()
        mavenCentral()
    }
}

rootProject.name = "StreamMaster"
include(":app")
