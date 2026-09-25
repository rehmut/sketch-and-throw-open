plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.potterywheeldetector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.potterywheeldetector"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "0.10.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    val cameraXVersion = "1.6.2"
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("androidx.camera:camera-mlkit-vision:$cameraXVersion")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mediapipe:tasks-vision:1.0.0")
    testImplementation("junit:junit:4.13.2")
}
