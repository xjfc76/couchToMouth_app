plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.couchtommouth.bridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.couchtommouth.bridge"
        minSdk = 26  // Android 8.0+ (covers Android 14 & 15)
        targetSdk = 34
        versionCode = 122
        versionName = "1.2.2"

        // Build config fields for easy configuration
        buildConfigField("String", "POS_URL", "\"https://pos.couchtomouth.com/\"")
        buildConfigField("String", "SUMUP_AFFILIATE_KEY", "\"sup_afk_UQLEOz5DtgiDiTveFpv3CAkObFE4GfoV\"")
        buildConfigField("String", "SUMUP_APP_ID", "\"CouchToMouth POS\"")
        buildConfigField("boolean", "AUTO_PRINT_CARD", "true")
        buildConfigField("boolean", "AUTO_PRINT_CASH", "false")
        buildConfigField("String", "UPDATE_URL", "\"https://pos.couchtomouth.com/couch2mouth-bridge-app/releases/version.json\"")
        buildConfigField("String", "SHOP_NAME", "\"CouchToMouth POS\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // WebView
    implementation("androidx.webkit:webkit:1.9.0")

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Bluetooth printing - ESC/POS library
    implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")

    // SumUp SDK
    implementation("com.sumup:merchant-sdk:5.0.2")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
