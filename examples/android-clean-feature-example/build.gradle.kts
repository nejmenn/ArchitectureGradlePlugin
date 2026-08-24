import br.com.nejmenn.architecture.android.DomainPurity

plugins {
    id("com.android.application") version "8.9.2" apply false
    id("com.android.library") version "8.9.2" apply false
    kotlin("android") version "2.1.21" apply false
    id("br.com.nejmenn.architecture") version "1.1.0"
}

architectureGradlePlugin {
    preset("android-clean-feature")
    basePackage = "br.com.nejmenn.sample"

    android {
        domainPurity = DomainPurity.STRICT
    }
}

