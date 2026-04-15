package cloud.fiely.file.service

import cloud.fiely.file.domain.FileMetadataEntity
import cloud.fiely.file.domain.FileMetadataId
import cloud.fiely.file.domain.FileMetadataRepository
import cloud.fiely.file.domain.FileRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Manages namespaced metadata documents attached to files.
 *
 * The namespace carves out write territories so producers don't clobber each
 * other. `user` is the usual caller-facing namespace for tags/ratings/notes;
 * extractors and AI plugins pick their own (typically their plugin id).
 */
@Service
class FileMetadataService(
    private val files: FileRepository,
    private val metadata: FileMetadataRepository,
    private val objectMapper: ObjectMapper,
) {
    fun list(ownerId: UUID, fileId: UUID): List<MetadataDocument> {
        requireOwned(ownerId, fileId)
        return metadata.findAllByIdFileId(fileId).map(::toDocument)
    }

    fun get(ownerId: UUID, fileId: UUID, namespace: String): MetadataDocument {
        requireOwned(ownerId, fileId)
        val ns = validateNamespace(namespace)
        val row = metadata.findById(FileMetadataId(fileId, ns)).orElseThrow {
            NotFoundException("No metadata in namespace '$ns' for this file")
        }
        return toDocument(row)
    }

    @Transactional
    fun put(ownerId: UUID, fileId: UUID, namespace: String, data: JsonNode): MetadataDocument {
        requireOwned(ownerId, fileId)
        val ns = validateNamespace(namespace)
        val serialized = objectMapper.writeValueAsString(data)
        val key = FileMetadataId(fileId, ns)
        val existing = metadata.findById(key).orElse(null)
        val saved = if (existing != null) {
            existing.data = serialized
            metadata.save(existing)
        } else {
            metadata.save(FileMetadataEntity(id = key, data = serialized))
        }
        return toDocument(saved)
    }

    @Transactional
    fun delete(ownerId: UUID, fileId: UUID, namespace: String) {
        requireOwned(ownerId, fileId)
        val ns = validateNamespace(namespace)
        val removed = metadata.deleteByIdFileIdAndIdNamespace(fileId, ns)
        if (removed == 0L) throw NotFoundException("No metadata in namespace '$ns' for this file")
    }

    private fun requireOwned(ownerId: UUID, fileId: UUID) {
        files.findByIdAndOwnerId(fileId, ownerId)
            ?: throw NotFoundException("File not found")
    }

    private fun toDocument(row: FileMetadataEntity): MetadataDocument = MetadataDocument(
        namespace = row.id.namespace,
        data = objectMapper.readTree(row.data),
        createdAt = row.createdAt.toString(),
        updatedAt = row.updatedAt.toString(),
    )

    private fun validateNamespace(raw: String): String {
        val ns = raw.trim()
        if (ns.isEmpty()) throw BadRequestException("Namespace must not be blank")
        if (ns.length > 64) throw BadRequestException("Namespace must be 64 chars or fewer")
        if (!NAMESPACE_RE.matches(ns)) {
            throw BadRequestException(
                "Namespace may only contain letters, digits, '-', '_' and '.'",
            )
        }
        return ns
    }

    data class MetadataDocument(
        val namespace: String,
        val data: JsonNode,
        val createdAt: String,
        val updatedAt: String,
    )

    companion object {
        private val NAMESPACE_RE = Regex("""^[A-Za-z0-9][A-Za-z0-9._-]*$""")
    }
}
