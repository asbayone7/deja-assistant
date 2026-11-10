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
        versionCode = 13
        versionName = "0.8.9"
        val dejaApiBaseUrl = providers.gradleProperty("DEJA_API_BASE_URL").orElse("").get().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "DEJA_API_BASE_URL", "\"$dejaApiBaseUrl\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { buildConfig = true }
}


dependencies {
    implementation("androidx.biometric:biometric:1.1.0")
}
