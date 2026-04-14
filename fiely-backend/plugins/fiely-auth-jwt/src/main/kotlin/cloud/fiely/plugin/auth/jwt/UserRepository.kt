package cloud.fiely.plugin.auth.jwt

import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

/**
 * Thin JDBC wrapper over the `auth_users` table created by
 * `db/migration/fiely-auth-jwt/V1__auth_users.sql`. Intentionally uses
 * raw JDBC so the plugin JAR stays small and doesn't need to bundle
 * spring-jdbc or JPA.
 */
class UserRepository(private val dataSource: DataSource) {

    fun findByUsername(username: String): AuthUserRow? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, email, display_name, password_hash, roles, is_enabled " +
                    "FROM auth_users WHERE username = ?"
            ).use { stmt ->
                stmt.setString(1, username)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }

    fun findById(id: String): AuthUserRow? =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT id, username, email, display_name, password_hash, roles, is_enabled " +
                    "FROM auth_users WHERE id = ?::uuid"
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) mapRow(rs) else null
                }
            }
        }

    fun insert(user: AuthUserRow): AuthUserRow =
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO auth_users (id, username, email, display_name, password_hash, roles, is_enabled) " +
                    "VALUES (?::uuid, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, user.id)
                stmt.setString(2, user.username)
                stmt.setString(3, user.email)
                stmt.setString(4, user.displayName)
                stmt.setString(5, user.passwordHash)
                stmt.setArray(6, conn.createArrayOf("text", user.roles.toTypedArray()))
                stmt.setBoolean(7, user.enabled)
                stmt.executeUpdate()
            }
            user
        }

    private fun mapRow(rs: ResultSet): AuthUserRow {
        val rolesArray = rs.getArray("roles")
        @Suppress("UNCHECKED_CAST")
        val roles = (rolesArray?.array as? Array<String>)?.toSet() ?: emptySet()
        return AuthUserRow(
            id = rs.getString("id"),
            username = rs.getString("username"),
            email = rs.getString("email"),
            displayName = rs.getString("display_name"),
            passwordHash = rs.getString("password_hash"),
            roles = roles,
            enabled = rs.getBoolean("is_enabled"),
        )
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

data class AuthUserRow(
    val id: String,
    val username: String,
    val email: String?,
    val displayName: String?,
    val passwordHash: String,
    val roles: Set<String>,
    val enabled: Boolean,
)
