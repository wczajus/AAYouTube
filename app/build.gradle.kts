plugins {
    id("com.android.application")
}

android {
    namespace = "pl.czajus.aayoutube"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.czajus.aayoutube"
        minSdk = 29
        targetSdk = 36
        // CI run number, so every build installs as an update (Google Play also requires this).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"

        // OnePlus Nord is arm64; GeckoView ships native libraries per ABI, so keep only this one.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // A fixed release key comes from the CI secrets (see README). Without it the release build
    // falls back to the debug key, which changes between CI runs.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    // Android Auto (phone projection) specific parts of the Car App Library.
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Release channel of GeckoView (Firefox engine). "+" picks the newest release;
    // pin the resolved version here once the first build succeeds.
    implementation("org.mozilla.geckoview:geckoview:+")
}
