package cloud.fiely.plugin.auth.jwt

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JwtTokenServiceTest {

    private val service = JwtTokenService(
        secret = "test-secret-not-used-in-production-at-least-32-chars",
        issuer = "fiely-test",
    )

    @Test
    fun `issued token can be verified and carries claims`() {
        val token = service.issue(
            subject = "user-123",
            claims = mapOf("username" to "alice", "roles" to "admin,user"),
            ttlSeconds = 60,
        )
        val claims = service.verify(token)
        assertNotNull(claims)
        assertEquals("user-123", claims!!["sub"])
        assertEquals("fiely-test", claims["iss"])
        assertEquals("alice", claims["username"])
        assertEquals("admin,user", claims["roles"])
    }

    @Test
    fun `tampered token fails verification`() {
        val token = service.issue("user-123", emptyMap(), ttlSeconds = 60)
        val tampered = token.dropLast(3) + "AAA"
        assertNull(service.verify(tampered))
    }

    @Test
    fun `expired token fails verification`() {
        val token = service.issue("user-123", emptyMap(), ttlSeconds = -10)
        assertNull(service.verify(token))
    }

    @Test
    fun `token signed with different secret fails verification`() {
        val token = service.issue("user-123", emptyMap(), ttlSeconds = 60)
        val other = JwtTokenService("completely-different-secret-at-least-32-chars", "fiely-test")
        assertNull(other.verify(token))
    }
}
