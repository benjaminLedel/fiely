package cloud.fiely.file.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileMetadataRepository : JpaRepository<FileMetadataEntity, FileMetadataId> {
    fun findAllByIdFileId(fileId: UUID): List<FileMetadataEntity>
    fun deleteByIdFileIdAndIdNamespace(fileId: UUID, namespace: String): Long
    fun deleteAllByIdFileIdIn(fileIds: Collection<UUID>): Long
}
