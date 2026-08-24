plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":architecture-core"))
    implementation(project(":architecture-presets"))
    implementation(project(":architecture-android"))
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

gradlePlugin {
    plugins {
        create("architectureGradlePlugin") {
            id = "br.com.nejmenn.architecture"
            implementationClass = "br.com.nejmenn.architecture.gradle.ArchitectureGradlePlugin"
            displayName = "ArchitectureGradlePlugin"
            description = "Validates Kotlin projects against configurable architecture rules."
        }
    }
}

java { withSourcesJar() }
