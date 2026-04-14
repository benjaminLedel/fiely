package cloud.fiely.plugin.auth.jwt

import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory

/**
 * PF4J plugin entrypoint for the built-in database-backed JWT auth plugin.
 *
 * The plugin exposes two extensions:
 *  - [AuthJwtMigrations]: ships the `auth_users` table migration.
 *  - [DatabaseAuthProvider]: authenticates username/password against that table
 *    and issues HS256 JWT tokens.
 *
 * Configuration is read from `fiely.plugins.fiely-auth-jwt.*`:
 *  - `secret`           — HMAC secret for signing tokens (required in production)
 *  - `issuer`           — `iss` claim, defaults to `fiely`
 *  - `access-token-ttl` — seconds, default 3600
 *  - `refresh-token-ttl`— seconds, default 604800 (7 days)
 */
class AuthJwtPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private val log = LoggerFactory.getLogger(AuthJwtPlugin::class.java)

    override fun start() {
        log.info("fiely-auth-jwt plugin started (v{})", wrapper.descriptor.version)
    }

    override fun stop() {
        log.info("fiely-auth-jwt plugin stopped")
    }
}
