plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStorePassword = System.getenv("PEPERS_KEYSTORE_PASSWORD")
val signingKeyPassword = System.getenv("PEPERS_KEY_PASSWORD")
val signingKeyAlias = System.getenv("PEPERS_KEY_ALIAS") ?: "key0"

android {
    namespace = "com.add.pepers"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.add.pepers"
        minSdk = 21
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("sharedRelease") {
            storeFile = file("release.jks")
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sharedRelease")
        }

        release {
            isDebuggable = false
            // Keep release behavior stable until runtime reflection/resource
            // paths are verified. R8 is intentionally disabled for now.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("sharedRelease")
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

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")

    implementation("androidx.activity:activity-compose:1.9.3")
    // Keep FragmentActivity compatible with Activity Result API request-code handling.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-core:1.7.5")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation("androidx.core:core-ktx:1.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
