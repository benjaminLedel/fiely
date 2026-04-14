package cloud.fiely.plugin

import cloud.fiely.plugin.PluginMigrations
import cloud.fiely.plugin.PluginServices
import org.pf4j.spring.ExtensionsInjector
import org.pf4j.spring.SpringPluginManager
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource

/**
 * Fiely's PF4J plugin manager.
 *
 * Ordering (enforced by the optional [FlywayMigrationInitializer] constructor
 * dependency and the overridden [init]):
 *  1. Spring Boot's Flyway auto-configuration runs core migrations
 *     (`db/migration/V*.sql`) via the `flywayInitializer` bean.
 *  2. [PluginServices] is populated so plugins can reach the shared
 *     [DataSource] from within their own classloader.
 *  3. Plugins are loaded and resolved (JARs discovered in `fiely.plugins.dir`).
 *  4. [PluginMigrationRunner] runs each plugin's migrations using the plugin's
 *     classloader, tracked in a dedicated `flyway_plugin_<id>_history` table.
 *  5. Plugins are started and their extensions injected into the Spring context.
 */
@Component
class FielyPluginManager(
    private val dataSource: DataSource,
    private val environment: ConfigurableEnvironment,
    // Declared to force ordering: when Flyway is enabled, its migration
    // initializer bean is created (and runs) before this one. In tests
    // where Flyway is disabled, the default value keeps Spring happy.
    @Suppress("UNUSED_PARAMETER") flywayInitializer: FlywayMigrationInitializer? = null,
) : SpringPluginManager(resolvePluginsRoot(environment)) {

    private val log = LoggerFactory.getLogger(FielyPluginManager::class.java)
    private val pluginsDir: Path = resolvePluginsRoot(environment)

    @PostConstruct
    override fun init() {
        PluginServices.initialise(dataSource, flattenFielyConfig(environment))

        log.info("Loading plugins from {}", pluginsDir)
        loadPlugins()

        runPluginMigrations()

        startPlugins()

        // Mirror what SpringPluginManager.init() normally does after startPlugins():
        val beanFactory = applicationContext.autowireCapableBeanFactory as AbstractAutowireCapableBeanFactory
        ExtensionsInjector(this, beanFactory).injectExtensions()

        log.info("Plugin manager started with {} plugin(s)", plugins.size)
    }

    private fun runPluginMigrations() {
        val migrations = getExtensions(PluginMigrations::class.java)
        if (migrations.isEmpty()) {
            log.debug("No plugin migrations to run")
            return
        }
        val runner = PluginMigrationRunner(dataSource)
        migrations.forEach { ext ->
            val classLoader = classLoaderFor(ext)
            if (classLoader == null) {
                // PF4J's extension finder also scans the system classloader,
                // which would let migrations from non-plugin sources execute
                // unintentionally (e.g. when a plugin module is on the test
                // classpath). Plugin migrations must come from a real plugin.
                log.warn(
                    "Skipping PluginMigrations extension '{}' — not owned by any loaded plugin",
                    ext.pluginId,
                )
                return@forEach
            }
            runner.migrate(ext, classLoader)
        }
    }

    private fun classLoaderFor(extension: Any): ClassLoader? =
        getPlugins().firstOrNull { wrapper ->
            wrapper.pluginClassLoader === extension.javaClass.classLoader
        }?.pluginClassLoader

    companion object {
        private fun resolvePluginsRoot(env: ConfigurableEnvironment): Path {
            val configured = env.getProperty("fiely.plugins.dir", "./plugins")
            val path = Paths.get(configured).toAbsolutePath()
            val dir = path.toFile()
            if (!dir.exists()) dir.mkdirs()
            return path
        }

        private fun flattenFielyConfig(env: ConfigurableEnvironment): Map<String, Any?> {
            val snapshot = LinkedHashMap<String, Any?>()
            env.propertySources.forEach { source ->
                if (source is EnumerablePropertySource<*>) {
                    source.propertyNames.forEach { name ->
                        if (name.startsWith("fiely.") && name !in snapshot) {
                            snapshot[name] = env.getProperty(name)
                        }
                    }
                }
            }
            return snapshot
        }
    }
}
