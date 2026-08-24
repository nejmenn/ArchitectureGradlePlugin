plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "br.com.nejmenn.sample.data"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}

dependencies {
    implementation(project(":core:domain"))
    implementation("androidx.room:room-runtime:2.7.1")
}

