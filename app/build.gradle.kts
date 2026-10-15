import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

// Legacy Android Auto SDK (projection mode). Google never published it; Fermata Auto keeps a copy.
// Downloaded from a fixed Fermata commit and checked against a known SHA-256, not stored in this repo.
val aautoAar: File = file("libs/aauto.aar")
val aautoAarUrl =
    "https://raw.githubusercontent.com/AndreyPavlenko/Fermata/" +
        "7d2bd42e0fea98a796ba069b56c48ef07d874549/fermata/lib/auto/aauto.aar"
val aautoAarSha256 = "99337c3b591ac9670c12b508da38886aedba61dd494f39f5f166f02580ec584b"

fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

if (!aautoAar.exists() || sha256(aautoAar) != aautoAarSha256) {
    aautoAar.parentFile.mkdirs()
    URI(aautoAarUrl).toURL().openStream().use { input -> aautoAar.outputStream().use { input.copyTo(it) } }
    check(sha256(aautoAar) == aautoAarSha256) {
        aautoAar.delete()
        "aauto.aar checksum mismatch, refusing to build"
    }
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
    implementation(files(aautoAar))
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Release channel of GeckoView (Firefox engine). "+" picks the newest release;
    // pin the resolved version here once the first build succeeds.
    implementation("org.mozilla.geckoview:geckoview:+")
}
