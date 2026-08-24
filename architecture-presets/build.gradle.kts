plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":architecture-core"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
}

java { withSourcesJar() }
publishing { publications { create<MavenPublication>("maven") { from(components["java"]) } } }

