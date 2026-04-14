plugins {
    kotlin("jvm")
}

description = "Fiely plugin — text extraction from PDF, DOCX, TXT"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
