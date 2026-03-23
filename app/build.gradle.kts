//plugins {
//    alias(libs.plugins.android.application)
//    alias(libs.plugins.kotlin.android)
//    alias(libs.plugins.compose.compiler)
//}
//
//android {
//    namespace = "com.runanywhere.kotlin_starter_example"
//    compileSdk = 35
//
//    defaultConfig {
//        applicationId = "com.runanywhere.kotlin_starter_example"
//        minSdk = 26
//        targetSdk = 35
//        versionCode = 1
//        versionName = "1.0"
//
//        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
//
//        vectorDrawables {
//            useSupportLibrary = true
//        }
//    }
//
//    buildTypes {
//        release {
//            isMinifyEnabled = false
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
//        }
//    }
//
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_17
//        targetCompatibility = JavaVersion.VERSION_17
//    }
//
//    kotlinOptions {
//        jvmTarget = "17"
//    }
//
//    buildFeatures {
//        compose = true
//    }
//
//    packaging {
//        resources {
//            excludes += "/META-INF/{AL2.0,LGPL2.1}"
//        }
//    }
//}
//
//dependencies {
//    // AndroidX Core
//    implementation(libs.androidx.core.ktx)
//    implementation(libs.androidx.appcompat)
//    implementation(libs.material)
//
//    // Jetpack Compose
//    implementation(libs.androidx.activity.compose)
//    implementation(platform(libs.androidx.compose.bom))
//    implementation(libs.androidx.compose.ui)
//    implementation(libs.androidx.compose.ui.graphics)
//    implementation(libs.androidx.compose.ui.tooling.preview)
//    implementation(libs.androidx.compose.material3)
//    implementation(libs.androidx.compose.material.icons.extended)
//    implementation(libs.androidx.lifecycle.viewmodel.compose)
//    implementation(libs.androidx.navigation.compose)
//    debugImplementation(libs.androidx.compose.ui.tooling)
//
//    // Coroutines
//    implementation(libs.kotlinx.coroutines.core)
//    implementation(libs.kotlinx.coroutines.android)
//
//    // RunAnywhere SDK
//    implementation(libs.runanywhere.sdk)
//    implementation(libs.runanywhere.llamacpp)
//    implementation(libs.runanywhere.onnx)
//
//    // Testing
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
//}
// app/build.gradle.kts
// ─────────────────────────────────────────────────────────────────────────────
// Code Finalizer — build.gradle.kts
// Replace your existing app/build.gradle.kts with this file.
// Adds ML Kit OCR + FlowRow on top of the existing RunAnywhere dependencies.
// ─────────────────────────────────────────────────────────────────────────────
// app/build.gradle.kts
// Code Finalizer — patched on top of original RunAnywhere starter
// Only change from your original: added ML Kit OCR dependency at the bottom

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)   // ← your original alias, NOT kotlin.compose
}

android {
    namespace = "com.runanywhere.kotlin_starter_example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runanywhere.kotlin_starter_example"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── AndroidX Core (unchanged from original) ───────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ── Jetpack Compose (unchanged from original) ─────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Coroutines (unchanged from original) ──────────────────────────────
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // ── RunAnywhere SDK (unchanged from original) ─────────────────────────
    implementation(libs.runanywhere.sdk)
    implementation(libs.runanywhere.llamacpp)
    implementation(libs.runanywhere.onnx)

    // ── NEW: ML Kit OCR — only addition needed for Code Finalizer ─────────
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // ── Testing (unchanged from original) ─────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
