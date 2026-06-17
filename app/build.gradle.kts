plugins {
    id("com.android.application")
}

android {
    namespace = "com.jchshi.readalong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jchshi.readalong"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.1"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/readalong-release.jks")
            storePassword = "readalong-release"
            keyAlias = "readalong"
            keyPassword = "readalong-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:143.0.20251003115653")
}
