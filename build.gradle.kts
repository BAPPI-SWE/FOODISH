// Top-level build file...
plugins {
    // Updated the Android Gradle Plugin version
    id("com.android.application") version "8.10.1" apply false
    // Updated the Kotlin version to be compatible
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false  // ← add this


    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false


}