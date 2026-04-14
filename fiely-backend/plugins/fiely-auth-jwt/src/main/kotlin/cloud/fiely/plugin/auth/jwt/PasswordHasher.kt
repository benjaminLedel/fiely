package cloud.fiely.plugin.auth.jwt

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 password hasher. Encoded format:
 *
 * ```
 * pbkdf2$sha256$<iterations>$<salt-b64>$<hash-b64>
 * ```
 *
 * Chosen over bcrypt/argon2 because PBKDF2 is in the JDK and requires no
 * extra dependencies inside the plugin JAR. Iteration count is deliberately
 * conservative and can be tuned via the `fiely.plugins.fiely-auth-jwt.pbkdf2-iterations`
 * config key.
 */
class PasswordHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt, iterations)
        val b64 = Base64.getEncoder()
        return "pbkdf2\$sha256\$$iterations\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
    }

    fun verify(password: CharArray, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 5 || parts[0] != "pbkdf2" || parts[1] != "sha256") return false
        val iters = parts[2].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return false
        val expected = runCatching { b64.decode(parts[4]) }.getOrNull() ?: return false
        val actual = pbkdf2(password, salt, iters)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        private const val DEFAULT_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
