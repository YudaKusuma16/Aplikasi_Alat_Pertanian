plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.aplikasialatpertanian"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aplikasialatpertanian"
        minSdk = 24
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase BoM (Penting untuk mengelola versi secara otomatis)
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))

    // Tambahkan library untuk Cloud Firestore
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Firebase Storage
    implementation ("com.google.firebase:firebase-storage-ktx")

    // Image Loading Library (Glide)
    implementation ("com.github.bumptech.glide:glide:4.16.0")

    // Permission handling
    implementation ("com.karumi:dexter:6.2.3")
}