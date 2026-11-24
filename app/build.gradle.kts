plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.litephone.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.litephone.launcher"
        minSdk = 26
        targetSdk = 35
        // CI sets GITHUB_RUN_NUMBER so every build can install over the previous one.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "build ${System.getenv("GITHUB_RUN_NUMBER") ?: "dev"}"
        // Where Options > Updates looks for new builds (the latest GitHub release of this repo).
        val repo = System.getenv("GITHUB_REPOSITORY") ?: "realcaseygg-afk/Lite_Phone"
        buildConfigField("String", "UPDATE_URL", "\"https://github.com/$repo/releases/latest/download/LitePhone.apk\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // One fixed key so new builds install as updates over old ones.
    // It only identifies this personal, sideloaded app; it is not a store key.
    signingConfigs {
        create("litephone") {
            storeFile = rootProject.file("keystore/litephone-signing.jks")
            storePassword = "android"
            keyAlias = "litephone"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("litephone")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("litephone")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
