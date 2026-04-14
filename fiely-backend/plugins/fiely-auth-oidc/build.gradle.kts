plugins {
    kotlin("jvm")
}

description = "Fiely plugin — OIDC / Keycloak authentication"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
