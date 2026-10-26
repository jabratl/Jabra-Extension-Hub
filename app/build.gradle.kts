plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingStorePath = System.getenv("JABRA_SIGNING_STORE_FILE")
val signingStorePassword = System.getenv("JABRA_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("JABRA_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("JABRA_SIGNING_KEY_PASSWORD")
val hasStableSigning = !signingStorePath.isNullOrBlank() &&
    !signingStorePassword.isNullOrBlank() && !signingKeyAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank() && file(signingStorePath).isFile

android {
    namespace = "com.jabra.hub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jabra.hub"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "0.9.1"
    }
    signingConfigs {
        if (hasStableSigning) {
            create("stableRelease") {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stableRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
}
