package cloud.fiely.file.service

import cloud.fiely.file.domain.FileEntity
import cloud.fiely.file.domain.FileRepository
import cloud.fiely.plugin.FileReference
import cloud.fiely.plugin.StoragePath
import cloud.fiely.plugin.StorageProvider
import cloud.fiely.tenant.domain.TenantEntity
import cloud.fiely.tenant.domain.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.util.UUID

/**
 * Orchestrates the file tree in the DB and delegates binary I/O to the
 * active [StorageProvider] plugin.
 *
 * The StorageProvider bean is supplied lazily via [ObjectProvider] because
 * PF4J injects plugin-contributed extensions *after* regular Spring beans are
 * constructed — capturing a `List<StorageProvider>` at constructor time would
 * race with plugin startup (same pattern as `AuthController`).
 */
@Service
class FileService(
    private val files: FileRepository,
    private val tenants: TenantRepository,
    private val storageProviders: ObjectProvider<StorageProvider>,
    @Value("\${fiely.storage.provider:fiely-storage-local}")
    private val activeStorageId: String,
) {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    // --- Queries ------------------------------------------------------------

    fun list(ownerId: UUID, parentId: UUID?): List<FileEntity> {
        if (parentId != null) {
            // Parent must exist and belong to the caller, else 404.
            val parent = files.findByIdAndOwnerId(parentId, ownerId)
                ?: throw NotFoundException("Folder not found")
            if (!parent.isFolder) throw BadRequestException("Parent is not a folder")
            return files.findAllByOwnerIdAndParentIdOrderByIsFolderDescNameAsc(ownerId, parentId)
        }
        return files.findAllByOwnerIdAndParentIdIsNullOrderByIsFolderDescNameAsc(ownerId)
    }

    fun get(ownerId: UUID, id: UUID): FileEntity =
        files.findByIdAndOwnerId(id, ownerId) ?: throw NotFoundException("File not found")

    // --- Mutations ----------------------------------------------------------

    @Transactional
    fun createFolder(ownerId: UUID, tenantId: UUID, parentId: UUID?, name: String): FileEntity {
        val cleanName = validateName(name)
        requireParentIsOwnedFolder(ownerId, parentId)
        ensureUniqueName(ownerId, parentId, cleanName)

        val folder = FileEntity(
            tenantId = tenantId,
            ownerId = ownerId,
            parentId = parentId,
            name = cleanName,
            isFolder = true,
        )
        return files.save(folder)
    }

    @Transactional
    fun upload(
        ownerId: UUID,
        tenantId: UUID,
        parentId: UUID?,
        name: String,
        contentType: String?,
        size: Long,
        content: InputStream,
    ): FileEntity {
        val cleanName = validateName(name)
        requireParentIsOwnedFolder(ownerId, parentId)
        ensureUniqueName(ownerId, parentId, cleanName)

        val tenant = tenants.findById(tenantId).orElseThrow {
            NotFoundException("Tenant not found")
        }
        enforceUploadLimit(tenant, size)

        val provider = activeProvider()
        val fileId = UUID.randomUUID()
        val storagePath = StoragePath(
            tenantId = tenantId.toString(),
            userId = ownerId.toString(),
            fileId = fileId.toString(),
            version = 1,
        )

        val ref: FileReference = provider.store(storagePath, content, size)

        val entity = FileEntity(
            id = fileId,
            tenantId = tenantId,
            ownerId = ownerId,
            parentId = parentId,
            name = cleanName,
            isFolder = false,
            sizeBytes = size,
            contentType = contentType,
            storageId = ref.storageId,
            storagePath = ref.path,
            currentVersion = 1,
        )
        return files.save(entity)
    }

    fun openForDownload(ownerId: UUID, id: UUID): Pair<FileEntity, InputStream> {
        val file = get(ownerId, id)
        if (file.isFolder) throw BadRequestException("Cannot download a folder")
        val ref = FileReference(
            storageId = file.storageId ?: throw IllegalStateException("File row missing storage_id"),
            path = file.storagePath ?: throw IllegalStateException("File row missing storage_path"),
        )
        val provider = providerFor(file.storageId!!)
            ?: throw ServiceUnavailableException("Storage provider '${file.storageId}' is not available")
        return file to provider.retrieve(ref)
    }

    @Transactional
    fun update(ownerId: UUID, id: UUID, newName: String?, newParentId: UUID?, moveToRoot: Boolean): FileEntity {
        val file = get(ownerId, id)

        val targetName = newName?.let { validateName(it) } ?: file.name
        val targetParent = when {
            moveToRoot -> null
            newParentId != null -> newParentId
            else -> file.parentId
        }

        if (targetParent != null) {
            requireParentIsOwnedFolder(ownerId, targetParent)
            if (targetParent == file.id) throw BadRequestException("Cannot move a folder into itself")
            if (file.isFolder && isDescendantOf(ancestorId = file.id, candidateId = targetParent, ownerId = ownerId)) {
                throw BadRequestException("Cannot move a folder into its own descendant")
            }
        }

        val nameOrParentChanged = targetName != file.name || targetParent != file.parentId
        if (nameOrParentChanged) {
            ensureUniqueName(ownerId, targetParent, targetName, excludeId = file.id)
        }

        file.name = targetName
        file.parentId = targetParent
        return files.save(file)
    }

    @Transactional
    fun delete(ownerId: UUID, id: UUID) {
        val file = get(ownerId, id)
        // Walk the tree first so we can ask the StorageProvider to drop each
        // blob. DB cascade handles the row deletes once we return.
        val blobsToDelete = mutableListOf<FileEntity>()
        collectBlobs(file, ownerId, blobsToDelete)

        val providerCache = HashMap<String, StorageProvider?>()
        for (blob in blobsToDelete) {
            val sid = blob.storageId ?: continue
            val spath = blob.storagePath ?: continue
            val provider = providerCache.getOrPut(sid) { providerFor(sid) }
            if (provider == null) {
                log.warn("Storage provider '{}' not available — leaving blob {} orphaned on delete", sid, blob.id)
                continue
            }
            runCatching { provider.delete(FileReference(sid, spath)) }
                .onFailure { log.warn("Failed to delete blob for file {}: {}", blob.id, it.message) }
        }

        files.delete(file)
    }

    // --- Internals ----------------------------------------------------------

    private fun activeProvider(): StorageProvider {
        return providerFor(activeStorageId)
            ?: throw ServiceUnavailableException(
                "Active storage provider '$activeStorageId' is not registered",
            )
    }

    private fun providerFor(id: String): StorageProvider? =
        storageProviders.stream().toList().firstOrNull { it.id == id }

    private fun enforceUploadLimit(tenant: TenantEntity, size: Long) {
        if (size > tenant.maxUploadBytes) {
            throw PayloadTooLargeException(
                "Upload of $size bytes exceeds tenant limit of ${tenant.maxUploadBytes} bytes",
            )
        }
    }

    private fun requireParentIsOwnedFolder(ownerId: UUID, parentId: UUID?) {
        if (parentId == null) return
        val parent = files.findByIdAndOwnerId(parentId, ownerId)
            ?: throw NotFoundException("Parent folder not found")
        if (!parent.isFolder) throw BadRequestException("Parent is not a folder")
    }

    private fun ensureUniqueName(ownerId: UUID, parentId: UUID?, name: String, excludeId: UUID? = null) {
        val exists = files.existsByOwnerIdAndParentIdAndName(ownerId, parentId, name)
        if (!exists) return
        // If the caller is renaming/moving onto its own current location, allow it.
        if (excludeId != null) {
            val currentHolder = files.findAllByOwnerIdAndParentIdOrderByIsFolderDescNameAsc(ownerId, parentId)
                .firstOrNull { it.name == name }
            if (currentHolder?.id == excludeId) return
        }
        throw ConflictException("A file or folder named '$name' already exists here")
    }

    private fun validateName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw BadRequestException("Name must not be blank")
        if (trimmed.length > 255) throw BadRequestException("Name must be 255 chars or fewer")
        if (trimmed.contains('/') || trimmed.contains('\\')) {
            throw BadRequestException("Name must not contain path separators")
        }
        if (trimmed == "." || trimmed == "..") throw BadRequestException("Reserved name")
        return trimmed
    }

    private fun isDescendantOf(ancestorId: UUID, candidateId: UUID, ownerId: UUID): Boolean {
        var cursor: UUID? = candidateId
        val visited = HashSet<UUID>()
        while (cursor != null) {
            if (!visited.add(cursor)) return false // cycle guard
            if (cursor == ancestorId) return true
            cursor = files.findByIdAndOwnerId(cursor, ownerId)?.parentId
        }
        return false
    }

    private fun collectBlobs(node: FileEntity, ownerId: UUID, acc: MutableList<FileEntity>) {
        if (!node.isFolder) {
            acc += node
            return
        }
        for (child in files.findAllByOwnerIdAndParentId(ownerId, node.id)) {
            collectBlobs(child, ownerId, acc)
        }
    }
}

// --- Exceptions the controller maps to HTTP statuses ------------------------

class NotFoundException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class PayloadTooLargeException(message: String) : RuntimeException(message)
class ServiceUnavailableException(message: String) : RuntimeException(message)
