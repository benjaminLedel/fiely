package cloud.fiely.plugin.storage.local

import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory

/**
 * PF4J plugin entrypoint for the built-in filesystem storage provider.
 *
 * The plugin exposes a single extension — [LocalStorageProvider] — that writes
 * uploaded blobs under a configurable root directory.
 *
 * Configuration is read from `fiely.plugins.fiely-storage-local.*`:
 *  - `root` — root directory for blob storage (defaults to `./data/blobs`)
 */
class StorageLocalPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private val log = LoggerFactory.getLogger(StorageLocalPlugin::class.java)

    override fun start() {
        log.info("fiely-storage-local plugin started (v{})", wrapper.descriptor.version)
    }

    override fun stop() {
        log.info("fiely-storage-local plugin stopped")
    }
}
