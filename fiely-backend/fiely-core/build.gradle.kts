plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

description = "Fiely core — Spring Boot application and plugin manager"

dependencies {
    implementation(project(":fiely-plugin-api"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.pf4j:pf4j-spring:0.9.0") {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
    }
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // OpenAPI 3 / Swagger UI — exposes /v3/api-docs and /swagger-ui.html.
    // The webmvc-ui starter pulls in swagger-ui webjar and the core springdoc
    // library, so both the raw spec and the interactive UI are available
    // out of the box.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // Note: we deliberately do NOT depend on the fiely-auth-jwt project here.
    // Doing so would put the plugin's META-INF/extensions.idx on the test
    // classpath and PF4J would auto-discover its extensions via the system
    // classloader — bypassing plugin isolation and breaking other tests.
    // Instead, the integration test loads the plugin as a real JAR via the
    // `copyTestPlugins` task and seeds its own test user with an inline
    // PBKDF2 hasher (see TestPasswordHasher in the integration test).
    testRuntimeOnly("com.h2database:h2")
}

// --- Test plugin packaging ---------------------------------------------------
//
// Build the fiely-auth-jwt plugin as a real JAR and drop it into
// `build/test-plugins/` before tests run, then expose that path to the test
// JVM via a system property. The end-to-end integration test points
// `fiely.plugins.dir` at this directory so PF4J actually discovers, resolves
// and starts the plugin — no stubs involved.
val copyTestPlugins = tasks.register<Copy>("copyTestPlugins") {
    val pluginJar = project(":plugins:fiely-auth-jwt").tasks.named("jar")
    dependsOn(pluginJar)
    from(pluginJar)
    into(layout.buildDirectory.dir("test-plugins"))
}

tasks.test {
    dependsOn(copyTestPlugins)
    systemProperty(
        "fiely.test.plugins.dir",
        layout.buildDirectory.dir("test-plugins").get().asFile.absolutePath,
    )
}
