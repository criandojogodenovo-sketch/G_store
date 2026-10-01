rootProject.name = "g-store-api"

// Provisão automática de toolchain JDK (usado no CI quando o JDK 21
// não estiver pré-instalado no runner).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

