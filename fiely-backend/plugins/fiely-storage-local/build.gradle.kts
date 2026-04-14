plugins {
    kotlin("jvm")
}

description = "Fiely plugin — local filesystem storage"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
