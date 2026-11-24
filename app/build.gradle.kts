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
        // The list of your other apps that Updates can update (my-apps.json in this repo).
        buildConfigField("String", "APPS_LIST_URL", "\"https://raw.githubusercontent.com/$repo/HEAD/my-apps.json\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Release builds are signed with Lite Phone's private key, which lives only in
    // GitHub secrets (never in this repo). The phone only accepts updates signed
    // with it. Builds without the key come out unsigned.
    val keystoreFile = System.getenv("LITEPHONE_KEYSTORE_FILE")
    val keystorePassword = System.getenv("LITEPHONE_KEYSTORE_PASSWORD")
    signingConfigs {
        if (!keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank()) {
            create("litephone") {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = "litephone"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfigs.findByName("litephone")?.let { signingConfig = it }
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
