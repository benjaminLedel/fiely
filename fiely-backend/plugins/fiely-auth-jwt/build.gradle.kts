plugins {
    kotlin("jvm")
}

description = "Fiely plugin — JWT / database authentication"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
