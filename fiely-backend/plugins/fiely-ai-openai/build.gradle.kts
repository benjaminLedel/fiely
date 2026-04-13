plugins {
    kotlin("jvm")
}

description = "Fiely plugin — OpenAI API provider"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
