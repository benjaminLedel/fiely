package cloud.fiely.plugin

import kotlinx.coroutines.flow.Flow
import org.pf4j.ExtensionPoint

/**
 * Extension point for pluggable AI backends (embedding, completion, chat).
 *
 * Implementations ship as separate plugins (e.g. fiely-ai-ollama, fiely-ai-openai).
 * AI is fully opt-in — Fiely works without any AIProvider installed.
 */
interface AIProvider : ExtensionPoint {
    val id: String
    val displayName: String

    fun embed(text: String): List<Float>
    fun complete(prompt: String): String
    fun chat(messages: List<ChatMessage>): Flow<String>

    fun isAvailable(): Boolean
}

data class ChatMessage(
    val role: Role,
    val content: String
) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}
