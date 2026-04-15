package cloud.fiely.file.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A node in the file tree — either a folder (`isFolder = true`,
 * `storageId`/`storagePath` null) or a regular file (`isFolder = false`,
 * `storageId`/`storagePath` set). The XOR invariant is enforced by the
 * `files_folder_xor_blob` check constraint in V4__files.sql.
 *
 * Parentage is a plain self-reference — root nodes have `parentId = null`.
 * Cascading delete at the DB level handles folder subtree removal; the service
 * layer walks descendants first to delete blobs via the StorageProvider.
 */
@Entity
@Table(name = "files")
class FileEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(name = "owner_id", nullable = false)
    val ownerId: UUID,

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(name = "is_folder", nullable = false)
    val isFolder: Boolean = false,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0,

    @Column(name = "content_type", length = 255)
    var contentType: String? = null,

    @Column(name = "storage_id", length = 64)
    var storageId: String? = null,

    @Column(name = "storage_path")
    var storagePath: String? = null,

    @Column(name = "current_version", nullable = false)
    var currentVersion: Int = 1,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PrePersist
    fun onCreate() {
        val now = OffsetDateTime.now()
        // createdAt is val; only updatedAt needs a PrePersist touch-up to match
        // the DB default. Hibernate will persist the already-set createdAt.
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}
