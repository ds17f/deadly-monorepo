import java.util.Properties
import java.io.ByteArrayOutputStream

// Load version from version.properties (single source of truth for both Android and iOS)
val versionPropsFile = rootProject.file("../version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { versionProps.load(it) }
}
val appVersionName = versionProps.getProperty("VERSION_NAME") ?: "1.0.0"
val appVersionCode = versionProps.getProperty("VERSION_CODE")?.toIntOrNull() ?: 1

// Function to get git commit hash (configuration cache friendly)
fun getGitCommitHash(): Provider<String> {
    return try {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText.map { it.trim() }.orElse("worktree-build")
    } catch (e: Exception) {
        providers.provider { "worktree-build" }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.grateful.deadly"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.grateful.deadly"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Add build config fields for version information
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
        buildConfigField("int", "VERSION_CODE", "${versionCode}")
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    // Check for signing keystore before creating signing config
    val keystorePropsFile = rootProject.file("../.secrets/keystore.properties")
    val hasReleaseSigningConfig = keystorePropsFile.exists()

    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                val keystoreProps = Properties()
                keystoreProps.load(keystorePropsFile.inputStream())

                // Resolve storeFile path relative to monorepo root
                val storeFilePath = keystoreProps["storeFile"] as String
                storeFile = rootProject.file("../$storeFilePath")
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
        println("Release signing configuration loaded from .secrets/keystore.properties")
    } else {
        println("WARNING: .secrets/keystore.properties not found - release builds will use debug signing")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false

            // Add build type and git commit hash for debug builds
            buildConfigField("String", "BUILD_TYPE", "\"debug\"")
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use release signing if available, otherwise fall back to debug signing
            signingConfig = if (hasReleaseSigningConfig) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }

            // Add build type and no commit hash for release builds
            buildConfigField("String", "BUILD_TYPE", "\"release\"")
            buildConfigField("String", "GIT_COMMIT_HASH", "\"\"")
        }

        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Dependencies
    implementation(project(":core:design"))
    implementation(project(":core:theme"))
    implementation(project(":core:theme-api"))
    implementation(project(":core:media"))
    implementation(project(":core:player"))
    implementation(project(":core:miniplayer"))
    implementation(project(":core:database"))
    implementation(project(":core:api:home"))
    implementation(project(":core:home"))
    implementation(project(":core:api:collections"))
    implementation(project(":core:collections"))
    implementation(project(":core:api:recent"))
    implementation(project(":core:recent"))

    // Feature Dependencies
    implementation(project(":feature:splash"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:playlist"))
    implementation(project(":feature:player"))
    implementation(project(":feature:miniplayer"))
    implementation(project(":feature:library"))
    implementation(project(":feature:collections"))
    implementation(project(":feature:settings"))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 (for annotations)
    implementation("androidx.media3:media3-common:1.3.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")

    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    testImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
