package cloud.fiely.plugin.auth.jwt

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimal HS256 JWT encoder/decoder with no external dependencies.
 *
 * The payload is a flat `Map<String, Any?>` serialised as JSON. Keeping the
 * payload flat means we don't need Jackson inside the plugin JAR.
 */
class JwtTokenService(
    private val secret: String,
    private val issuer: String = "fiely",
) {

    private val mac: Mac by lazy {
        Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        }
    }

    fun issue(subject: String, claims: Map<String, Any?>, ttlSeconds: Long): String {
        val now = Instant.now()
        val payload = LinkedHashMap<String, Any?>().apply {
            put("iss", issuer)
            put("sub", subject)
            put("iat", now.epochSecond)
            put("exp", now.plusSeconds(ttlSeconds).epochSecond)
            putAll(claims)
        }
        val header = mapOf("alg" to "HS256", "typ" to "JWT")
        val encodedHeader = encode(jsonOf(header))
        val encodedPayload = encode(jsonOf(payload))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = encodeBytes(sign(signingInput.toByteArray(StandardCharsets.UTF_8)))
        return "$signingInput.$signature"
    }

    /**
     * Verify the token's signature and expiry. Returns the decoded claims on
     * success, `null` on any failure. Callers must never trust an unverified
     * token.
     */
    fun verify(token: String): Map<String, Any?>? {
        val parts = token.split('.')
        if (parts.size != 3) return null
        val (encodedHeader, encodedPayload, encodedSignature) = parts
        val signingInput = "$encodedHeader.$encodedPayload".toByteArray(StandardCharsets.UTF_8)
        val expected = sign(signingInput)
        val actual = runCatching { Base64.getUrlDecoder().decode(encodedSignature) }.getOrNull() ?: return null
        if (!constantTimeEquals(expected, actual)) return null
        val payloadJson = runCatching {
            String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val claims = parseJsonObject(payloadJson) ?: return null
        val exp = (claims["exp"] as? Number)?.toLong() ?: return null
        if (Instant.now().epochSecond >= exp) return null
        return claims
    }

    private fun sign(input: ByteArray): ByteArray = synchronized(mac) { mac.doFinal(input) }

    private fun encode(raw: String): String =
        encodeBytes(raw.toByteArray(StandardCharsets.UTF_8))

    private fun encodeBytes(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // --- Tiny JSON (flat objects, string/number/boolean/null values only) ---

    private fun jsonOf(map: Map<String, Any?>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) sb.append(',')
            appendJsonString(sb, k)
            sb.append(':')
            appendJsonValue(sb, v)
        }
        return sb.append('}').toString()
    }

    private fun appendJsonValue(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v.toString())
            is Number -> sb.append(v.toString())
            else -> appendJsonString(sb, v.toString())
        }
    }

    private fun appendJsonString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                else ->
                    if (c.code < 0x20) sb.append("\\u%04x".format(c.code))
                    else sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun parseJsonObject(json: String): Map<String, Any?>? {
        // Minimal JSON parser tolerant of whitespace. Supports strings,
        // numbers, booleans, null at the top level of a flat object.
        val parser = JsonParser(json)
        return parser.parseObject()
    }

    private class JsonParser(private val src: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any?>? {
            skip()
            if (!consume('{')) return null
            val result = LinkedHashMap<String, Any?>()
            skip()
            if (consume('}')) return result
            while (true) {
                skip()
                val key = parseString() ?: return null
                skip()
                if (!consume(':')) return null
                skip()
                val value = parseValue() ?: return if (peek()?.equals('n') == true) null else null
                result[key] = value
                skip()
                if (consume('}')) return result
                if (!consume(',')) return null
            }
        }

        private fun parseValue(): Any? {
            skip()
            return when (peek()) {
                '"' -> parseString()
                't', 'f' -> parseBool()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseString(): String? {
            if (!consume('"')) return null
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' && pos < src.length -> {
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (pos + 4 > src.length) return null
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> return null
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return null
        }

        private fun parseBool(): Boolean? = when {
            src.startsWith("true", pos) -> { pos += 4; true }
            src.startsWith("false", pos) -> { pos += 5; false }
            else -> null
        }

        private fun parseNull(): Any? = if (src.startsWith("null", pos)) { pos += 4; null } else null

        private fun parseNumber(): Number? {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' ||
                    src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) pos++
            val text = src.substring(start, pos)
            return if (text.contains('.') || text.contains('e') || text.contains('E'))
                text.toDoubleOrNull()
            else text.toLongOrNull()
        }

        private fun peek(): Char? = if (pos < src.length) src[pos] else null
        private fun consume(c: Char): Boolean =
            if (pos < src.length && src[pos] == c) { pos++; true } else false
        private fun skip() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
    }
}
