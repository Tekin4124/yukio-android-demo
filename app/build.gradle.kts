import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

// Demo traffic is restricted to the local backend. No production default.
val demoBaseUrl = (project.findProperty("DEMO_BASE_URL") as String?)
    ?: "http://127.0.0.1:3000/"
val demoUri = URI(demoBaseUrl)
require(demoUri.scheme == "http" && demoUri.host in setOf("127.0.0.1", "localhost", "10.0.2.2") &&
    demoUri.userInfo == null && demoUri.query == null && demoUri.fragment == null && demoBaseUrl.endsWith("/")) {
    "DEMO_BASE_URL must be a local HTTP URL ending in /"
}

android {
    namespace = "com.example.test"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yukio.demo"
        minSdk = 26
        targetSdk = 36
        versionCode = 42
        versionName = "1.0.6-demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BASE_URL", "\"$demoBaseUrl\"")
        buildConfigField("String", "PRIVACY_URL", "\"${demoBaseUrl}privacy\"")
        buildConfigField("String", "PARSER_API_KEY", "\"\"")
        buildConfigField("String", "DEBUG_PANEL_URL", "\"\"")
        buildConfigField("String", "MAPS_API_KEY", "\"\"")
        buildConfigField("String", "PLAY_SUBSCRIPTION_PRODUCT_ID", "\"demo_disabled\"")
        buildConfigField("boolean", "TELEMETRY_ENABLED", "false")
        buildConfigField("int", "TRIAL_DAYS", "30")
        manifestPlaceholders["MAPS_API_KEY"] = ""
    }
    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Extended Icons
    implementation(libs.androidx.compose.material.icons.extended)

    // Google Maps
    implementation("com.google.maps.android:maps-compose:4.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Maps Utils (Heatmap için)
    implementation("com.google.maps.android:android-maps-utils:3.8.0")
    
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation("androidx.fragment:fragment:1.5.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Play Billing. Product prices and eligible offers are always read
    // from ProductDetails; no price is compiled into the app.
    implementation("com.android.billingclient:billing:9.1.0")

    // Google Play flexible in-app updates.
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    add("ksp", "androidx.room:room-compiler:2.6.1")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
