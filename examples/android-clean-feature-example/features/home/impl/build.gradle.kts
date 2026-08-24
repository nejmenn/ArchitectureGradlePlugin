plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "br.com.nejmenn.sample.features.home.impl"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":features:home:api"))
    implementation("androidx.compose.runtime:runtime:1.8.2")
}

