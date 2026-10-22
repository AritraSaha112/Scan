plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.folioscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.folioscan"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Shared key so every CI build installs over the previous one.
    // For a public release, replace with your own key kept in GitHub Secrets.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/folioscan-dev.jks")
            storePassword = "folioscan"
            keyAlias = "folioscan"
            keyPassword = "folioscan"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device document scanner (edge detection, crop, cleanup, gallery import)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // On-device OCR (bundled models: works offline)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // PDF writing: compression, invisible OCR text layer, password protection
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
