plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A GitHub Actions futás sorszáma lesz a verziószám, így minden új APK frissítésként települ
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.tpj.water"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tpj.water"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
    }

    // Közös aláíró kulcs: minden felhőben fordított APK ugyanazzal a kulccsal készül,
    // így az új verzió adatvesztés nélkül települ a régire. Play Áruházhoz külön, titkos kulcs kell.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/hydrofren-debug.jks")
            storePassword = "hydrofren"
            keyAlias = "hydrofren"
            keyPassword = "hydrofren"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
    implementation("androidx.core:core-ktx:1.13.1")
}
