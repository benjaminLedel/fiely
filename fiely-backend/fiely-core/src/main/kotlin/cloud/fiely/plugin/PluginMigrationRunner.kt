package cloud.fiely.plugin

import cloud.fiely.plugin.PluginMigrations
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Runs Flyway migrations declared by plugins via the [PluginMigrations]
 * extension point.
 *
 * Each plugin gets its own `flyway_plugin_<sanitised-id>_history` table so
 * plugin migrations are tracked independently from core migrations, and from
 * each other. Migration scripts are resolved using the plugin's own
 * classloader so they can live inside the plugin JAR.
 */
class PluginMigrationRunner(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(PluginMigrationRunner::class.java)

    fun migrate(extension: PluginMigrations, classLoader: ClassLoader) {
        val locations = extension.migrationLocations
        if (locations.isEmpty()) {
            log.debug("Plugin {} declares no migration locations", extension.pluginId)
            return
        }

        val historyTable = historyTableFor(extension.pluginId)
        log.info(
            "Running migrations for plugin '{}' from {} (history: {})",
            extension.pluginId, locations, historyTable
        )

        val flyway = Flyway.configure(classLoader)
            .dataSource(dataSource)
            .locations(*locations.toTypedArray())
            .table(historyTable)
            .baselineOnMigrate(true)
            .placeholders(extension.placeholders)
            // Plugin migrations are independent — don't fail because the
            // core schema already has objects.
            .validateOnMigrate(false)
            .load()

        val result = flyway.migrate()
        log.info(
            "Plugin '{}' migrations applied: {} (success={})",
            extension.pluginId, result.migrationsExecuted, result.success
        )
    }

    private fun historyTableFor(pluginId: String): String {
        // Postgres identifiers: lower-case, alphanumerics + underscore, <= 63 chars.
        val sanitised = pluginId.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val name = "flyway_plugin_${sanitised}_history"
        return if (name.length > 63) name.substring(0, 63) else name
    }
}
