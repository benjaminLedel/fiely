package cloud.fiely.plugin.auth.jwt

import cloud.fiely.plugin.PluginMigrations
import org.pf4j.Extension

/**
 * Declares the schema migrations shipped with this plugin. The core's
 * `PluginMigrationRunner` discovers this extension and runs the referenced
 * SQL files using the plugin's own classloader, tracked in a dedicated
 * `flyway_plugin_fiely_auth_jwt_history` table.
 */
@Extension
class AuthJwtMigrations : PluginMigrations {
    override val pluginId: String = "fiely-auth-jwt"
    override val migrationLocations: List<String> =
        listOf("classpath:db/migration/fiely-auth-jwt")
}
