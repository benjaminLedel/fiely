package cloud.fiely.file.web

import cloud.fiely.file.domain.FileEntity
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Metadata for a single file or folder.")
data class FileMetadataResponse(
    val id: UUID,
    val parentId: UUID?,
    val name: String,
    val isFolder: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val currentVersion: Int,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(file: FileEntity): FileMetadataResponse = FileMetadataResponse(
            id = file.id,
            parentId = file.parentId,
            name = file.name,
            isFolder = file.isFolder,
            sizeBytes = file.sizeBytes,
            contentType = file.contentType,
            currentVersion = file.currentVersion,
            createdAt = file.createdAt,
            updatedAt = file.updatedAt,
        )
    }
}

@Schema(description = "Request to create a new folder.")
data class CreateFolderRequest(
    val parentId: UUID? = null,
    val name: String,
)

@Schema(description = "Partial update — rename and/or move a node.")
data class UpdateFileRequest(
    val name: String? = null,
    val parentId: UUID? = null,
    /**
     * Discriminator because `parentId = null` is a legal target (the root).
     * When `moveToRoot = true`, `parentId` is ignored and the node is moved
     * to the root; otherwise `parentId` is only applied if present.
     */
    val moveToRoot: Boolean = false,
)
