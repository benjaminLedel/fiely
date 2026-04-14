package cloud.fiely.plugin

import org.pf4j.ExtensionPoint

/**
 * Extension point that allows a plugin to ship its own database schema migrations.
 *
 * Migration files are packaged in the plugin JAR (typically under
 * `db/migration/<plugin-id>/`) and follow Flyway's naming convention,
 * e.g. `V1__create_tables.sql`, `V2__add_index.sql`.
 *
 * The core runs Flyway separately for each plugin using the plugin's own
 * classloader and a dedicated schema history table so that plugin migrations
 * are tracked independently from core migrations.
 */
interface PluginMigrations : ExtensionPoint {
    /**
     * The plugin identifier. Should match the `plugin.id` in `plugin.properties`.
     * Used to derive a dedicated Flyway schema history table
     * (`flyway_plugin_<id>_history`) so the plugin's migrations are tracked
     * separately from the core schema.
     */
    val pluginId: String

    /**
     * Classpath locations (Flyway-style) where migrations live.
     * Example: `listOf("classpath:db/migration/fiely-auth-jwt")`
     *
     * Locations are resolved using the plugin's own classloader.
     */
    val migrationLocations: List<String>

    /**
     * Optional Flyway placeholders that are substituted into migration scripts.
     */
    val placeholders: Map<String, String>
        get() = emptyMap()
}
