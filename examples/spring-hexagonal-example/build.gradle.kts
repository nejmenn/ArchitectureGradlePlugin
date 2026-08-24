plugins {
    java
    id("br.com.nejmenn.architecture") version "1.1.0"
}

architectureGradlePlugin {
    preset("spring-hexagonal")
    basePackage = "br.com.nejmenn.orion"

    domain {
        modelSuffix = "Domain"
        forbiddenImports.addAll(
            "org.springframework.",
            "br.com.nejmenn.orion.infrastructure.",
            "br.com.nejmenn.orion.web.",
        )
    }

    naming {
        oneTypePerFile = true
        filenameMustMatchType = true
        forbiddenSuffix("Service", "UseCase")
    }

    repositories { requireSuspendFunctions = true }
    serialization {
        allowed("kotlinx.serialization")
        forbidden("com.fasterxml.jackson", "org.codehaus.jackson")
    }

    sources {
        include("**/src/main/kotlin/**/*.kt")
        exclude("**/build/**", "**/generated/**")
    }
}
