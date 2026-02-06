import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.kotlin.android)
            alias(libs.plugins.google.devtools.ksp)
            alias(libs.plugins.google.dagger.hilt.android)
            alias(libs.plugins.compose.compiler)
        }

// FIX: Replaced deprecated 'android' block with 'configure<ApplicationExtension>'
configure<ApplicationExtension> {
    namespace = "com.example.dashpilot"
    compileSdk = 36 // Modern assignment syntax

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
            // Note: getDefaultProguardFile is legacy.
            // Ensure 'proguard-rules.pro' exists in your app module.
            proguardFiles("proguard-rules.pro")
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

    // Extended Icons
    implementation(libs.androidx.material.icons.extended)

    // --- VIBE: BRAINS (Room Database) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- VIBE: VISION (ML Kit) ---
    implementation(libs.play.services.mlkit.text.recognition)

    // --- VIBE: WIRING (Hilt) ---
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.location)
    implementation(libs.androidx.documentfile)
}