package cloud.fiely.plugin.auth.jwt

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordHasherTest {

    private val hasher = PasswordHasher(iterations = 1_000) // keep tests fast

    @Test
    fun `verify accepts correct password`() {
        val encoded = hasher.hash("hunter2".toCharArray())
        assertTrue(hasher.verify("hunter2".toCharArray(), encoded))
    }

    @Test
    fun `verify rejects wrong password`() {
        val encoded = hasher.hash("hunter2".toCharArray())
        assertFalse(hasher.verify("hunter3".toCharArray(), encoded))
    }

    @Test
    fun `two hashes of the same password differ due to salt`() {
        val a = hasher.hash("same".toCharArray())
        val b = hasher.hash("same".toCharArray())
        assertNotEquals(a, b)
    }

    @Test
    fun `verify rejects malformed encoded strings`() {
        assertFalse(hasher.verify("p".toCharArray(), "not-a-valid-hash"))
        assertFalse(hasher.verify("p".toCharArray(), "pbkdf2\$sha256\$abc\$xx\$yy"))
    }
}
