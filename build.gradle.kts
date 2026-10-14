plugins {
    id("com.android.application") version "9.1.0" apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin). Declaring the Kotlin plugin here only raises
    // the Kotlin compiler version it uses, to match the kotlin-stdlib pulled in by AndroidX.
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
}
