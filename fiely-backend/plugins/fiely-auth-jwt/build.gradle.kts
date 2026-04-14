plugins {
    kotlin("jvm")
}

description = "Fiely plugin — JWT / database authentication"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
    compileOnly("org.pf4j:pf4j:3.12.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure the plugin JAR manifest with PF4J metadata so the plugin
// manager can identify and load it.
tasks.jar {
    manifest {
        attributes(
            "Plugin-Id" to "fiely-auth-jwt",
            "Plugin-Version" to project.version.toString(),
            "Plugin-Provider" to "Fiely",
            "Plugin-Class" to "cloud.fiely.plugin.auth.jwt.AuthJwtPlugin",
            "Plugin-Description" to "Database-backed JWT authentication (built-in default)",
        )
    }
}
