plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.deadly.v2.core.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    // V2 Core dependencies
    implementation(project(":v2:core:api:player"))
    implementation(project(":v2:core:media"))
    implementation(project(":v2:core:domain"))
    implementation(project(":v2:core:model"))
    
    // Media3 for MediaMetadata access
    implementation("androidx.media3:media3-common:1.4.1")
    
    // Hilt for dependency injection
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    
    // Coroutines for StateFlow operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}