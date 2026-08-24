plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "br.com.nejmenn.sample"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.nejmenn.sample"
        minSdk = 24
        targetSdk = 35
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":features:home:api"))
    implementation(project(":features:home:impl"))
}

