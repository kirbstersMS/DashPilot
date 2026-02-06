import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.dagger.hilt.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.dashpilot"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.gigpilot.doordash"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // --- VIBE: UI & NAVIGATION (Compose) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.hilt.navigation.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Extended Icons (Fixes Icons.Filled.DragHandle, etc.)
    implementation(libs.androidx.material.icons.extended)

    // --- VIBE: BRAINS (Room Database) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx) // Adds Coroutines support
    ksp(libs.androidx.room.compiler)      // The compiler

    // --- VIBE: VISION (ML Kit) ---
    // Text Recognition v2 (Latin script)
    implementation(libs.play.services.mlkit.text.recognition)

    // --- VIBE: WIRING (Hilt) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.location)

    implementation(libs.androidx.documentfile)
}