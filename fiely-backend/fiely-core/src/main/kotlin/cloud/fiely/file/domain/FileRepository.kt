package cloud.fiely.file.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileRepository : JpaRepository<FileEntity, UUID> {
    fun findByIdAndOwnerId(id: UUID, ownerId: UUID): FileEntity?

    fun findAllByOwnerIdAndParentIdOrderByIsFolderDescNameAsc(
        ownerId: UUID,
        parentId: UUID?,
    ): List<FileEntity>

    fun findAllByOwnerIdAndParentIdIsNullOrderByIsFolderDescNameAsc(
        ownerId: UUID,
    ): List<FileEntity>

    fun findAllByOwnerIdAndParentId(ownerId: UUID, parentId: UUID): List<FileEntity>

    fun existsByOwnerIdAndParentIdAndName(ownerId: UUID, parentId: UUID?, name: String): Boolean
}
