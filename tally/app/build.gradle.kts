plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.killswitch.tally"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.killswitch.tally"
        minSdk = 26
        targetSdk = 34 // foreground-service rules differ per target level, so it stays at 34 until tested
        // CI numbers every build; a local build falls back to 100, which still beats the old 6.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = 100 + (run ?: 0)
        versionName = "1.0." + (run ?: "dev")
        manifestPlaceholders["appLabel"] = "Tally"
    }

    buildTypes {
        // The app you install is this one: optimized with R8 (a debug build runs cold, unoptimized code and feels slower).
        // Same package and the same fixed key every time, so a new build installs over the old one and keeps its settings.
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    signingConfigs {
        getByName("debug") {
            // A fixed, public debug key (the standard "android" credentials) so every build is signed the same and installs over the last one.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += arrayOf("DebugProbesKt.bin", "kotlin-tooling-metadata.json", "kotlin/**", "META-INF/*.version", "META-INF/**/LICENSE.txt")
        }
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("org.jetbrains.compose.ui:ui-graphics:1.12.0")
    implementation("org.jetbrains.compose.material:material-ripple:1.12.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
