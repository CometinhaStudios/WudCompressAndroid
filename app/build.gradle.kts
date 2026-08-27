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
        versionCode = 4
        versionName = "2.2-mobile"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
