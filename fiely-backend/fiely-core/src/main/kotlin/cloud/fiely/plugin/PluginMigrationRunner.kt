package cloud.fiely.plugin

import cloud.fiely.plugin.PluginMigrations
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import javax.sql.DataSource

/**
 * Runs Flyway migrations declared by plugins via the [PluginMigrations]
 * extension point.
 *
 * Each plugin gets its own `flyway_plugin_<sanitised-id>_history` table so
 * plugin migrations are tracked independently from core migrations, and from
 * each other. Migration scripts live inside the plugin JAR.
 *
 * **Why we extract migrations to a temp directory rather than passing
 * `classpath:` locations to Flyway**: Flyway 10's classpath scanner doesn't
 * reliably enumerate JAR entries when given a non-system classloader (e.g.
 * PF4J's `PluginClassLoader`). It silently returns zero SQL files and reports
 * a successful migration of nothing — exactly the failure mode we hit on PR
 * #2 (`Plugin '...' migrations applied: 0 (success=true)` while the table the
 * test queried didn't exist). Extracting via the plugin classloader (which
 * does work) and then handing Flyway a `filesystem:` location sidesteps that
 * scanner entirely.
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
        val tempRoot = Files.createTempDirectory("fiely-plugin-migrations-${sanitisedId(extension.pluginId)}-")
        val filesystemLocations = mutableListOf<String>()
        var totalFiles = 0
        for (loc in locations) {
            val classpathPath = loc.removePrefix("classpath:").trimStart('/')
            val targetDir = tempRoot.resolve(classpathPath).also { Files.createDirectories(it) }
            val copied = extractClasspathDir(classLoader, classpathPath, targetDir)
            totalFiles += copied
            filesystemLocations.add("filesystem:${targetDir.toAbsolutePath()}")
        }

        log.info(
            "Running migrations for plugin '{}' from {} (extracted {} file(s); history: {})",
            extension.pluginId, locations, totalFiles, historyTable,
        )

        val flyway = Flyway.configure(classLoader)
            .dataSource(dataSource)
            .locations(*filesystemLocations.toTypedArray())
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
            extension.pluginId, result.migrationsExecuted, result.success,
        )
    }

    /**
     * Copy every file under [classpathPath] (recursively) from the plugin
     * classloader to [targetDir]. Returns the number of files copied.
     */
    private fun extractClasspathDir(classLoader: ClassLoader, classpathPath: String, targetDir: Path): Int {
        val urls = classLoader.getResources(classpathPath).toList()
        if (urls.isEmpty()) {
            log.warn("Migration location '{}' resolved to no resources via classloader", classpathPath)
            return 0
        }
        var count = 0
        for (url in urls) {
            count += when (url.protocol) {
                "jar" -> extractFromJar(url, classpathPath, targetDir)
                "file" -> extractFromFilesystem(url, targetDir)
                else -> {
                    log.warn("Unsupported URL protocol for migration location: {}", url)
                    0
                }
            }
        }
        return count
    }

    private fun extractFromJar(url: URL, classpathPath: String, targetDir: Path): Int {
        val conn = url.openConnection() as JarURLConnection
        val jarFile = conn.jarFile
        val pathPrefix = if (classpathPath.endsWith("/")) classpathPath else "$classpathPath/"
        var count = 0
        val entries = jarFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            if (!entry.name.startsWith(pathPrefix)) continue
            val relative = entry.name.removePrefix(pathPrefix)
            val outFile = targetDir.resolve(relative)
            Files.createDirectories(outFile.parent)
            jarFile.getInputStream(entry).use { input ->
                Files.copy(input, outFile, StandardCopyOption.REPLACE_EXISTING)
            }
            count++
        }
        return count
    }

    private fun extractFromFilesystem(url: URL, targetDir: Path): Int {
        val src = Paths.get(url.toURI())
        if (!Files.isDirectory(src)) return 0
        var count = 0
        Files.walk(src).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val rel = src.relativize(file).toString()
                val dest = targetDir.resolve(rel)
                Files.createDirectories(dest.parent)
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING)
                count++
            }
        }
        return count
    }

    private fun historyTableFor(pluginId: String): String {
        val name = "flyway_plugin_${sanitisedId(pluginId)}_history"
        return if (name.length > 63) name.substring(0, 63) else name
    }

    private fun sanitisedId(pluginId: String): String =
        pluginId.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}
