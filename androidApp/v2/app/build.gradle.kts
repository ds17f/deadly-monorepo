plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlinx-serialization")
}

android {
    namespace = "com.deadly.v2.app"
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
    
    buildFeatures {
        compose = true
    }
    
}

dependencies {
    // V2 Core Dependencies (only design needed for pure navigation app)
    implementation(project(":v2:core:design"))
    implementation(project(":v2:core:theme"))
    implementation(project(":v2:core:theme-api"))
    implementation(project(":v2:core:media"))
    implementation(project(":v2:core:player"))
    implementation(project(":v2:core:api:home"))
    implementation(project(":v2:core:home"))
    implementation(project(":v2:core:api:collections"))
    implementation(project(":v2:core:collections"))
    implementation(project(":v2:core:api:recent"))
    implementation(project(":v2:core:recent"))

    // V2 Feature Dependencies
    implementation(project(":v2:feature:splash"))
    implementation(project(":v2:feature:home"))
    implementation(project(":v2:feature:search"))
    implementation(project(":v2:feature:playlist"))
    implementation(project(":v2:feature:player"))
    implementation(project(":v2:feature:miniplayer"))
    implementation(project(":v2:feature:library"))
    implementation(project(":v2:feature:collections"))
    implementation(project(":v2:feature:settings"))
    
    // Android & Compose
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

