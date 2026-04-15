package cloud.fiely.file.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.io.Serializable
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Namespaced metadata document attached to a [FileEntity].
 *
 * Each `(file_id, namespace)` pair holds a single JSON document. Producers
 * pick their own namespace so user tags, extractor output (EXIF, PDF info),
 * and AI-generated annotations don't collide. The DB stores `data` as TEXT
 * for portability — see the V5 migration for the path to JSONB.
 */
@Entity
@Table(name = "file_metadata")
class FileMetadataEntity(
    @EmbeddedId
    val id: FileMetadataId,

    @Column(name = "data", columnDefinition = "TEXT", nullable = false)
    var data: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}

@Embeddable
data class FileMetadataId(
    @Column(name = "file_id", nullable = false)
    val fileId: UUID = UUID(0, 0),

    @Column(name = "namespace", nullable = false, length = 64)
    val namespace: String = "",
) : Serializable
