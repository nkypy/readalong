plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jchshi.readalong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jchshi.readalong"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
