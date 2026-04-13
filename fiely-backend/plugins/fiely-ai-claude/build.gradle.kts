plugins {
    kotlin("jvm")
}

description = "Fiely plugin — Anthropic Claude API provider"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
