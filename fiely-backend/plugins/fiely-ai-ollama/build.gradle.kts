plugins {
    kotlin("jvm")
}

description = "Fiely plugin — Ollama AI provider (local)"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
