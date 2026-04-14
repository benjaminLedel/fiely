plugins {
    kotlin("jvm")
}

description = "Fiely plugin — LDAP / Active Directory authentication"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
}
