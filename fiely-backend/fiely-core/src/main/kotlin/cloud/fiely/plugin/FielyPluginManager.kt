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
 *  3. Plugins are loaded, resolved, and **started**. PF4J's
 *     `LegacyExtensionFinder` only returns extensions for plugins in state
 *     `STARTED`, so [getExtensions] would return an empty list before this
 *     point. Plugin `start()` callbacks must therefore not touch the DB —
 *     they typically just log.
 *  4. [PluginMigrationRunner] runs each plugin's migrations using the
 *     plugin's classloader, tracked in a dedicated
 *     `flyway_plugin_<id>_history` table.
 *  5. Extensions are injected into the Spring context. Beans that consume
 *     them must do so lazily (e.g. `ObjectProvider`) — they're registered
 *     after the rest of the context is built.
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

        // Start plugins BEFORE asking for extensions: PF4J's
        // LegacyExtensionFinder filters by PluginState.STARTED. If we called
        // getExtensions() while plugins were still RESOLVED, it would return
        // an empty list and migrations would silently no-op.
        startPlugins()

        runPluginMigrations()

        // Mirror what SpringPluginManager.init() normally does after startPlugins():
        val beanFactory = applicationContext.autowireCapableBeanFactory as AbstractAutowireCapableBeanFactory
        ExtensionsInjector(this, beanFactory).injectExtensions()

        log.info("Plugin manager started with {} plugin(s)", plugins.size)
    }

    private fun runPluginMigrations() {
        // Iterate started plugins explicitly and ask each for its
        // PluginMigrations extensions. Going via getExtensions(Class, pluginId)
        // is more robust than the global getExtensions(Class) lookup, which in
        // some PF4J configurations filters extensions in surprising ways.
        val started = getStartedPlugins()
        log.info("runPluginMigrations: scanning {} started plugin(s)", started.size)
        if (started.isEmpty()) return

        val runner = PluginMigrationRunner(dataSource)
        for (wrapper in started) {
            val pluginId = wrapper.descriptor.pluginId
            val classLoader = wrapper.pluginClassLoader
            val migrations = getExtensions(PluginMigrations::class.java, pluginId)
            log.info(
                "runPluginMigrations: plugin '{}' contributes {} PluginMigrations extension(s)",
                pluginId, migrations.size,
            )
            for (ext in migrations) {
                runner.migrate(ext, classLoader)
            }
        }
    }

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
