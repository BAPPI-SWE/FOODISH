plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"// Add this for Room




    id("org.jetbrains.kotlin.plugin.compose")  // ← add

}

android {
    namespace = "com.yumzy.userapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yumzy.userapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 17
        versionName = "12.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1") // Note: Updated some versions for stability
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Test Dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ADD THIS LINE TO INCLUDE THE FULL ICON LIBRARY
    implementation("androidx.compose.material:material-icons-extended")
    // ADD THIS FOR NAVIGATION
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // ADD THIS FOR GOOGLE SIGN-IN UI
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ADD THESE TWO LINES
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    // Add this line for Coil Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Add this for Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Room Database
    val room_version = "2.7.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Add this for Lottie animations in Compose
    implementation("com.airbnb.android:lottie-compose:6.4.1")

    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    implementation("com.google.android.gms:play-services-ads:23.1.0")

    // Add OneSignal dependency here
    implementation("com.onesignal:OneSignal:[5.0.0, 5.99.99]")

}