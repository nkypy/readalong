plugins {
    id("com.android.application")
}

dependencies {
    implementation("androidx.browser:browser:1.8.0")
}

android {
    namespace = "com.jchshi.readalong"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jchshi.readalong"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.0"
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
