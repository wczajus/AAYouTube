plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.czajus.aayoutube"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.czajus.aayoutube"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // OnePlus Nord is arm64; GeckoView ships native libraries per ABI, so keep only this one.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Release channel of GeckoView (Firefox engine). "+" picks the newest release;
    // pin the resolved version here once the first build succeeds.
    implementation("org.mozilla.geckoview:geckoview:+")
}
