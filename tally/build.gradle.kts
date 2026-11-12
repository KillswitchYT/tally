// Same toolchain as Kyant0/AndroidLiquidGlass 2.0.1. AGP 9 compiles Kotlin itself, so there is no kotlin-android plugin.
plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
