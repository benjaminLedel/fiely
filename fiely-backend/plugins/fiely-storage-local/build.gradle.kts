plugins {
    kotlin("jvm")
}

description = "Fiely plugin — local filesystem storage"

dependencies {
    compileOnly(project(":fiely-plugin-api"))
    compileOnly("org.pf4j:pf4j:3.12.0")
    compileOnly("org.slf4j:slf4j-api:2.0.16")

    testImplementation(project(":fiely-plugin-api"))
    testImplementation("org.pf4j:pf4j:3.12.0")
    testImplementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure the plugin JAR manifest with PF4J metadata so the plugin
// manager can identify and load it.
tasks.jar {
    manifest {
        attributes(
            "Plugin-Id" to "fiely-storage-local",
            "Plugin-Version" to project.version.toString(),
            "Plugin-Provider" to "Fiely",
            "Plugin-Class" to "cloud.fiely.plugin.storage.local.StorageLocalPlugin",
            "Plugin-Description" to "Filesystem-backed storage provider (built-in default)",
        )
    }
}
