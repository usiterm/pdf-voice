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
        versionCode = 2
        versionName = "0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
}
