# ArchitectureGradlePlugin

[![Build](https://github.com/nejmenn/ArchitectureGradlePlugin/actions/workflows/publish.yml/badge.svg)](https://github.com/nejmenn/ArchitectureGradlePlugin/actions/workflows/publish.yml)


`ArchitectureGradlePlugin` centralizes architecture checks for Kotlin/Spring Boot backends and Android applications. The same Gradle plugin discovers Kotlin sources across a multi-project build, analyzes every source once, and activates platform-specific rules through presets.

By moving deterministic architecture validation into the build pipeline, the plugin can also reduce LLM token usage in AI-assisted development workflows. Instead of requiring coding agents and language models to repeatedly inspect large portions of the codebase to understand and validate architectural constraints, those rules are encoded once and enforced automatically by Gradle.

This makes architecture checks deterministic, reusable, CI-friendly, and token-efficient, allowing LLMs to spend more of their context and tokens on implementation, reasoning, refactoring, and problem-solving rather than repeatedly rediscovering architecture rules from source code and documentation.

This is a public, open-source project hosted at [nejmenn/ArchitectureGradlePlugin](https://github.com/nejmenn/ArchitectureGradlePlugin).

**Publication status:** version `1.1.0` is published on GitHub Packages. The plugin ID is `br.com.nejmenn.architecture`, and the library group is `br.com.nejmenn`.

## Modules

| Module | Responsibility |
|---|---|
| `architecture-core` | Gradle-independent source model, lexer-based Kotlin structural parser, validation engine, stable rule IDs, and isolated rules. |
| `architecture-presets` | Named default configurations. Currently provides `spring-hexagonal`. |
| `architecture-android` | Android Clean Architecture presets and rules for framework purity, feature isolation, Compose, Room, components, and external dependencies. |
| `architecture-gradle-plugin` | Plugin DSL, cacheable Gradle tasks, multi-project discovery, Gradle module dependency inspection, reports, publication, and `check` integration. |
| `examples` | Standalone Spring and Android consumers using composite builds, so they can run without downloading published artifacts. |

The core has no Gradle API dependency. Gradle project dependencies are captured by the integration module and converted into violations using the same result model.

## Choose a platform preset

Apply the plugin once, preferably in the root `build.gradle.kts`, and select exactly one platform preset:

| Platform | Preset | Default Kotlin source roots |
|---|---|---|
| Spring/Kotlin backend | `spring-hexagonal` | `src/main/kotlin` |
| Android | `android-clean-feature` | `src/main/java` and `src/main/kotlin` |

Do not combine Spring and Android presets in the same application. Presets add defaults cumulatively, so mixing platform presets would also mix their naming and boundary rules.

Applying the plugin at the root makes it inspect every subproject. Applying it to a subproject limits discovery to that project and its descendants.

## Create your own Gradle preset

The built-in `preset("...")` function resolves presets shipped by ArchitectureGradlePlugin, but using one is optional. Because the plugin DSL is Kotlin, a build can define its own reusable preset as an extension function and apply it without selecting a built-in preset.

The following root `build.gradle.kts` defines an Android preset owned by the consuming project:

```kotlin
import br.com.nejmenn.architecture.android.DomainPurity
import br.com.nejmenn.architecture.gradle.ArchitectureGradlePluginExtension

plugins {
    id("br.com.nejmenn.architecture") version "1.1.0"
}

fun ArchitectureGradlePluginExtension.auroraAndroidPreset() {
    basePackage = "com.example.aurora"
    attachToCheck = true

    naming {
        oneTypePerFile = true
        filenameMustMatchType = true
        forbiddenSuffix(
            suffix = "Service",
            recommendation = "UseCase",
        )
    }

    android {
        domainPurity = DomainPurity.STRICT

        domainModulePatterns.add(":core:domain")
        featureApiModulePattern = ":features:*:api"
        featureImplementationModulePattern = ":features:*:impl"

        composeAllowedModulePatterns.addAll(
            ":app",
            ":core:ui",
            ":features:*:impl",
        )
        roomAllowedModulePatterns.add(":core:data")
        androidComponentAllowedModulePatterns.add(":app")
    }

    modules {
        modulePattern(":features:*:api") {
            mayDependOn(":core:domain", ":core:utils")
        }
        modulePattern(":features:*:impl") {
            mayDependOn(":core:**", ":features:*:api")
        }
    }

    sources {
        include("**/src/main/java/**/*.kt")
        include("**/src/main/kotlin/**/*.kt")
        exclude("**/build/**", "**/generated/**", "**/ksp/**")
    }
}

architectureGradlePlugin {
    auroraAndroidPreset()
}
```

This custom function is the project's preset: it can be called wherever the plugin is applied and can accept parameters when different applications need small variations. For reuse across multiple repositories, move the function into a Gradle convention plugin in `build-logic` and version that convention independently.

Do not also call `preset("android-clean-feature")` unless the intention is to extend its defaults. Preset and DSL configuration are cumulative; when replacing a collection inherited from a built-in preset, call `clear()` before adding the project's values.

## Recommended architectures — source of truth

The structures below are the canonical ArchitectureGradlePlugin recommendations for new projects. They define module responsibilities and dependency direction for each supported platform. A project may use fewer modules when its size does not justify every split, but it should preserve the same boundaries and inward dependency flow.

### Android source of truth

```text
:app
├── root navigation
├── dependency graph and composition root
├── Android entry points: Activity, receivers and services
└── notification runtime

:widgets
└── Jetpack Glance application widgets

:core
├── :utils          architecture primitives, MVI contracts, date/time and notification helpers
├── :domain         shared entities, repository ports, managers and validation
├── :data           Room databases, DAOs, migrations, data sources and repository implementations
├── :presentation   shared presentation models, state contracts and mappers
└── :ui             Material 3 theme, design tokens and reusable Compose components

:features
├── :home
│   ├── :api        public contracts, navigation API, inputs and outputs
│   └── :impl       UI, presentation, use cases, DI and internal navigation
├── :overview
│   ├── :api
│   └── :impl
├── :templates
│   ├── :api
│   └── :impl
├── :editor
│   ├── :api
│   └── :impl
├── :analytics
│   ├── :api
│   └── :impl
└── :settings
    ├── :api
    └── :impl
```

Recommended dependency direction:

| Module | May depend on |
|---|---|
| `:core:utils` | Nothing from the application architecture |
| `:core:domain` | `:core:utils` |
| `:core:data` | `:core:domain`, `:core:utils` |
| `:core:ui` | `:core:domain`, `:core:utils` |
| `:core:presentation` | `:core:domain`, `:core:utils`, `:core:ui` |
| `:features:<name>:api` | `:core:domain`, `:core:utils` |
| `:features:<name>:impl` | Any `:core:*` module and any feature `:api` |
| `:widgets` | Any `:core:*` module and feature APIs |
| `:app` | All modules; it is the composition root |

An `:api` module must never depend on an `:impl` module. A feature implementation may consume another feature's API, but it must not consume another feature implementation. Keep Android framework types out of `:core:domain`; keep Room in `:core:data`; and keep reusable Compose components in `:core:ui`. The preset enforces these boundaries and limits Android entry points to `:app` and `:widgets` by default.

### Spring source of truth

```text
:app
├── Spring Boot entry point
├── dependency graph and composition root
├── application configuration
└── runtime wiring

:shared
└── :kernel          identifiers, shared value objects, errors and domain primitives

:domain
├── :device          entities, value objects, policies and repository ports
├── :access          entities, value objects, policies and repository ports
├── :account         entities, value objects, policies and repository ports
└── :<context>       one module per bounded context when isolation is required

:application
├── :device          use cases, commands, queries and orchestration
├── :access          use cases, commands, queries and orchestration
├── :account         use cases, commands, queries and orchestration
└── :<context>       input and output ports for the bounded context

:adapter
├── :persistence     port adapters and persistence mappings
├── :security        authentication and authorization adapters
└── :integration     adapters for external systems

:infrastructure
├── :persistence     Spring Data/JPA, database configuration and migrations
├── :messaging       brokers, producers, consumers and technical configuration
└── :clients         HTTP clients, SDKs and vendor-specific configuration

:web
├── controllers
├── request and response DTOs
├── HTTP validation
└── exception mapping
```

Recommended dependency direction:

| Module | May depend on |
|---|---|
| `:shared:kernel` | Nothing from the application architecture |
| `:domain:<context>` | `:shared:kernel` |
| `:application:<context>` | Its domain module and `:shared:kernel` |
| `:adapter:*` | Application, domain and shared-kernel modules required by the adapter |
| `:infrastructure:*` | Application, domain and shared-kernel modules required by the implementation |
| `:web` | Application, domain and `:shared:kernel` |
| `:app` | All modules; it is the composition root |

Domain code must not import Spring or depend on application, adapter, infrastructure or web modules. Application code orchestrates domain behavior through ports and must not know controllers, databases, messaging brokers or vendor SDKs. Web, adapter and infrastructure modules are replaceable outer layers; they communicate with the application through its contracts. Cross-context collaboration should happen through explicit application contracts or shared-kernel concepts, not by reaching into another context's internals.

## Spring/Kotlin backend

### Minimal Spring configuration

```kotlin
plugins {
    id("br.com.nejmenn.architecture") version "1.1.0"
}

architectureGradlePlugin {
    preset("spring-hexagonal")
    basePackage = "com.example.myservice"
}
```

The Spring preset provides hexagonal layers, kotlinx serialization defaults, the `Domain` model suffix, suspending repositories, and the `Service` → `UseCase` naming convention.

The canonical Spring module graph and dependency rules are defined in [Spring source of truth](#spring-source-of-truth).

### Complete Spring DSL example

```kotlin
architectureGradlePlugin {
    preset("spring-hexagonal")
    basePackage = "com.example.myservice"
    attachToCheck = true

    domain {
        modelSuffix = "Domain"
        forbiddenImports.addAll(
            "org.springframework.",
            "com.example.myservice.infrastructure.",
            "com.example.myservice.web.",
        )
    }

    naming {
        oneTypePerFile = true
        filenameMustMatchType = true
        forbiddenSuffix(
            suffix = "Service",
            recommendation = "UseCase",
        )
        forbiddenPackage(
            packageFragment = "service",
            recommendation = "usecase",
        )
    }

    repositories {
        requireSuspendFunctions = true
        packageFragments.add("repository")
    }

    serialization {
        allowed("kotlinx.serialization")
        forbidden("com.fasterxml.jackson", "org.codehaus.jackson")
    }

    layers {
        layer("domain") {
            packageFragments.add("domain")
            forbiddenImports.add("org.springframework.")
        }
        layer("application") {
            mayDependOn("domain")
        }
        layer("adapter") {
            mayDependOn("application", "domain")
        }
        layer("infrastructure") {
            mayDependOn("application", "domain")
        }
        layer("web") {
            mayDependOn("application", "domain")
        }
    }

    modules {
        module(":domain:device") {
            mayDependOn(":shared:kernel")
        }
        module(":application:device") {
            mayDependOn(":domain:device", ":shared:kernel")
        }
    }

    sources {
        include("**/src/main/kotlin/**/*.kt")
        exclude("**/build/**", "**/generated/**")
    }
}
```

See the runnable Spring consumer in `examples/spring-hexagonal-example`.

## Android

### Minimal Android configuration

```kotlin
import br.com.nejmenn.architecture.android.DomainPurity

plugins {
    id("br.com.nejmenn.architecture") version "1.1.0"
}

architectureGradlePlugin {
    preset("android-clean-feature")
    basePackage = "com.example.myapp"

    android {
        domainPurity = DomainPurity.STRICT
    }
}
```

The Android preset supports Kotlin files in either Android source layout:

```text
src/main/java/**/*.kt
src/main/kotlin/**/*.kt
```

Projects may use either directory or both. Only `.kt` files are analyzed, and each file is analyzed once.

The canonical Android module graph and dependency rules are defined in [Android source of truth](#android-source-of-truth).

### Complete Android DSL example

```kotlin
import br.com.nejmenn.architecture.android.DomainPurity

architectureGradlePlugin {
    preset("android-clean-feature")
    basePackage = "com.example.myapp"
    attachToCheck = true

    android {
        domainPurity = DomainPurity.STRICT

        domainModulePatterns.add(":core:domain")
        domainAllowedImports.addAll(
            "javax.inject.",
            "kotlinx.coroutines.",
            "kotlinx.serialization.",
        )

        featureApiModulePattern = ":features:*:api"
        featureImplementationModulePattern = ":features:*:impl"

        composeAllowedModulePatterns.addAll(
            ":app",
            ":widgets",
            ":core:ui",
            ":core:presentation",
            ":features:*:impl",
        )
        roomAllowedModulePatterns.addAll(
            ":app",
            ":core:data",
        )
        androidComponentAllowedModulePatterns.addAll(
            ":app",
            ":widgets",
        )

        externalDependencyBoundary(
            technology = "SQLDelight",
            coordinatePrefixes = setOf("app.cash.sqldelight:"),
            allowedModulePatterns = setOf(":core:data"),
        )
    }

    repositories {
        requireSuspendFunctions = true
        packageFragments.add("repository")
        allowedNonSuspendReturnTypes.addAll(
            "Flow",
            "StateFlow",
            "SharedFlow",
        )
    }

    modules {
        modulePattern(":features:*:api") {
            mayDependOn(":core:utils", ":core:domain")
        }
        modulePattern(":features:*:impl") {
            mayDependOn(":core:**", ":features:*:api")
        }
    }

    sources {
        include("**/src/main/java/**/*.kt")
        include("**/src/main/kotlin/**/*.kt")
        exclude("**/build/**", "**/generated/**", "**/ksp/**")
    }
}
```

`DomainPurity.STRICT` rejects `android.*` and `androidx.*` imports in domain modules. `DomainPurity.PRAGMATIC` additionally permits `android.os.Parcelable`; both modes continue to enforce Compose and Room placement.

The preset already supplies the module patterns, source patterns, allowed asynchronous return types, Compose locations, and Room locations shown above. Repeat them only when documenting or extending the defaults. To replace a default collection, call `clear()` before `addAll(...)`.

See the runnable Android consumer in `examples/android-clean-feature-example`.

## Common DSL reference

| Property or block | Applies to | Purpose |
|---|---|---|
| `preset(name)` | Both | Selects the platform defaults. Use one platform preset. |
| `basePackage` | Both | Requires every analyzed Kotlin package to live below one canonical package. |
| `attachToCheck` | Both | Makes Gradle `check` depend on `architectureCheck`; defaults to `true`. |
| `sources` | Both | Adds include and exclude glob patterns. |
| `naming` | Both | Configures one type per file, filename matching, suffixes, and packages. |
| `repositories` | Both | Configures repository packages and non-blocking operations. |
| `serialization` | Both | Defines allowed and forbidden serialization imports. |
| `layers` | Primarily Spring | Defines package layers and their allowed dependency direction. |
| `modules.module(...)` | Both | Defines an exact Gradle module boundary. |
| `modules.modulePattern(...)` | Primarily Android | Defines a family of Gradle modules using `*` or `**`. |
| `domain` | Spring | Configures domain model suffixes and forbidden imports. |
| `android` | Android | Configures Android purity, feature boundaries, technology placement, and components. |

Presets are applied at the point where `preset(...)` is called. Statements after the preset can override scalar values or extend its collections.

## Execute checks on either platform

```bash
./gradlew architectureCheck
./gradlew architectureReport
./gradlew check
```

`architectureCheck` fails on `ERROR` violations. `architectureReport` always writes JSON, text, and HTML reports. Set `attachToCheck = false` when the architecture check should not participate in the normal Gradle `check` lifecycle.

## Consume library modules directly

Most projects only need the Gradle plugin. Tools that need the engine as a library can add the authenticated GitHub Packages repository to `dependencyResolutionManagement` and consume individual modules:

```kotlin
dependencies {
    implementation("br.com.nejmenn:architecture-core:1.1.0")
    implementation("br.com.nejmenn:architecture-presets:1.1.0")
    implementation("br.com.nejmenn:architecture-android:1.1.0")
}
```

## Rules and IDs

Rule IDs are defined centrally and do not derive from Kotlin class names:

| ID | Rule |
|---|---|
| `ARCH-001` | Layer dependency |
| `ARCH-002` | Forbidden import |
| `ARCH-003` | Canonical package |
| `ARCH-004` | Package/path correspondence |
| `ARCH-005` | One top-level type per file |
| `ARCH-006` | Forbidden type suffix |
| `ARCH-007` | Filename matches type |
| `ARCH-008` | Forbidden package |
| `ARCH-009` | Domain model suffix |
| `ARCH-010` | Suspending repository operations |
| `ARCH-011` | Serialization technology |
| `ARCH-012` | Gradle module dependency |
| `ARCH-013` | Android framework boundary |
| `ARCH-014` | Feature implementation leakage |
| `ARCH-015` | Feature API boundary |
| `ARCH-016` | Android technology placement |
| `ARCH-017` | Gradle external dependency boundary |
| `ARCH-018` | Android component placement |

## Reports

`architectureReport` writes:

```text
build/reports/architecture/
├── architecture-report.json
├── architecture-report.txt
└── architecture-report.html
```

The versioned JSON schema includes success state, counts, stable rule ID, severity, file, optional line, message, evidence, and recommendation. The report task always writes artifacts; `architectureCheck` is the task that fails on `ERROR` violations.

### Example output

A project that follows the configured architecture produces:

```text
> Task :architectureCheck
ArchitectureGradlePlugin Check
===========================

No violations found.

0 violation(s) found.
```

When a rule is violated, the output identifies the stable rule ID, source location, evidence, and recommended correction:

```text
> Task :architectureCheck
ArchitectureGradlePlugin Check
===========================

ARCH-006 Forbidden Type Suffix

src/main/kotlin/com/example/myservice/access/service/AccessService.kt:4

Types ending with 'Service' are forbidden.

Found:
AccessService

Recommended:
AccessUseCase

1 violation(s) found.

> Task :architectureCheck FAILED
Architecture check failed. 1 error violation(s) found.
```

## Build and test

```bash
./gradlew test
./gradlew build
```

Unit tests cover every core rule and parser behavior. Gradle TestKit tests cover task registration, valid and invalid projects, DSL/presets, reports, `check` attachment and module boundaries.

Run the example directly:

```bash
./gradlew -p examples/spring-hexagonal-example architectureCheck
./gradlew -p examples/android-clean-feature-example architectureCheck
```

## Implementation decisions

- Each file is read once and converted to `AnalyzedSource`; all source rules reuse that model.
- A purpose-built Kotlin lexer masks nested comments, strings and character literals and tracks brace depth. This avoids regex-only declaration counting without coupling the core to the Kotlin compiler implementation.
- Tasks declare source/configuration/module inputs and report/marker outputs and are build-cache enabled. Default patterns avoid generated/build directories.
- Layer validation is import-based; module validation inspects Gradle `ProjectDependency` instances, so it does not depend on parsing build scripts.
- The public API stays configuration-oriented: rule execution and Gradle mechanics do not leak into consumer builds.
