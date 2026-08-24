plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "br.com.nejmenn.sample.features.home.api"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
}

dependencies { implementation(project(":core:domain")) }

