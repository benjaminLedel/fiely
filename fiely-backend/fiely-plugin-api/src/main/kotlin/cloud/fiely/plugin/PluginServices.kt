package cloud.fiely.plugin

import javax.sql.DataSource

/**
 * Lightweight service locator that plugins can use to obtain core-managed
 * services (e.g. the shared [DataSource]).
 *
 * The core is responsible for populating these values during its bootstrap,
 * before plugins are started. Plugins should treat the accessors as read-only.
 *
 * This is intentionally minimal — third-party apps with richer needs should
 * use the sandboxed [AppContext] instead.
 */
object PluginServices {
    @Volatile
    private var _dataSource: DataSource? = null

    @Volatile
    private var _config: Map<String, Any?> = emptyMap()

    /**
     * The application [DataSource]. Only valid after the core has bootstrapped.
     * Throws [IllegalStateException] if accessed before the core has populated it.
     */
    val dataSource: DataSource
        get() = _dataSource
            ?: error("PluginServices.dataSource has not been initialised yet — called too early in the plugin lifecycle?")

    /**
     * Resolved configuration properties, keyed by `fiely.plugins.<plugin-id>.<key>`
     * style dotted paths. Useful for plugins that don't have access to Spring's
     * `Environment`.
     */
    val config: Map<String, Any?>
        get() = _config

    /**
     * Fetch the configuration sub-map for a specific plugin. For example,
     * `configFor("fiely-auth-jwt")` returns entries under
     * `fiely.plugins.fiely-auth-jwt.*`.
     */
    fun configFor(pluginId: String): Map<String, Any?> {
        val prefix = "fiely.plugins.$pluginId."
        return _config
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { (k, _) -> k.removePrefix(prefix) }
    }

    /** Only to be called by `fiely-core` during bootstrap. */
    fun initialise(dataSource: DataSource, config: Map<String, Any?>) {
        this._dataSource = dataSource
        this._config = config
    }
}
