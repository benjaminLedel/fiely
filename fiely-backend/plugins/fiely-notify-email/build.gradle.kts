plugins {
    kotlin("jvm")
}

description = "Fiely plugin — email notifications via SMTP"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
