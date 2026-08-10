plugins {
    id("com.android.application")
}

android {
    namespace = "com.pdfvoice.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pdfvoice.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
