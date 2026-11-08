plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.asbaygarcia.deja"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.asbaygarcia.deja"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }
}


dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
}
