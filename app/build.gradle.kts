plugins {
    id("com.android.application")
}

android {
    namespace = "com.wudcompress.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wudcompress.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0-java"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
