plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Updates for both WIPTV apps from GitHub releases, shared so either app's update pill can
// update the launcher, the player, or both.
android {
    namespace = "dev.wiptv.update"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
