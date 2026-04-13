plugins {
    kotlin("jvm")
}

description = "Fiely plugin API — shared interfaces and DTOs for all plugins"

dependencies {
    api("org.pf4j:pf4j:3.12.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
