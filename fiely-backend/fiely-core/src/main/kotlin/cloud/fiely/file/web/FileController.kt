package cloud.fiely.file.web

import cloud.fiely.auth.web.CurrentUserResolver
import cloud.fiely.auth.web.ErrorResponse
import cloud.fiely.file.service.BadRequestException
import cloud.fiely.file.service.ConflictException
import cloud.fiely.file.service.FileService
import cloud.fiely.file.service.NotFoundException
import cloud.fiely.file.service.PayloadTooLargeException
import cloud.fiely.file.service.ServiceUnavailableException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * REST endpoints for the file tree.
 *
 * Every endpoint authenticates the caller via [CurrentUserResolver] and
 * scopes queries to that user's rows. There is no Spring Security filter —
 * the pattern is consistent with [cloud.fiely.auth.web.AuthController].
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File tree management — upload, download, list, rename, move, delete.")
class FileController(
    private val fileService: FileService,
    private val currentUser: CurrentUserResolver,
) {

    // --- List & metadata ----------------------------------------------------

    @GetMapping
    @Operation(
        summary = "List children of a folder (or root)",
        description = "Returns immediate children ordered folders-first, then alphabetical. " +
            "Omit `parentId` to list the root.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Children listed"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Parent folder not found"),
        ],
    )
    fun list(
        @Parameter(description = "Parent folder id, or null for root")
        @RequestParam(required = false) parentId: UUID?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        val children = fileService.list(user.userId, parentId)
        return ResponseEntity.ok(children.map(FileMetadataResponse::from))
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get metadata for a single node",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Metadata returned"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Node not found"),
        ],
    )
    fun metadata(@PathVariable id: UUID, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        val node = fileService.get(user.userId, id)
        return ResponseEntity.ok(FileMetadataResponse.from(node))
    }

    // --- Upload / download --------------------------------------------------

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload a file",
        description = "Stores the uploaded bytes via the active StorageProvider and records " +
            "metadata. Upload size is capped by the caller's tenant `max_upload_bytes` setting.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "File stored"),
            ApiResponse(responseCode = "400", description = "Invalid name or parent"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Parent folder not found"),
            ApiResponse(responseCode = "409", description = "A node with that name already exists"),
            ApiResponse(responseCode = "413", description = "Upload exceeds the tenant limit"),
            ApiResponse(responseCode = "503", description = "No storage provider available"),
        ],
    )
    fun upload(
        @RequestPart("file") file: MultipartFile,
        @RequestParam(required = false) parentId: UUID?,
        @RequestParam(required = false) name: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        val effectiveName = name?.takeIf { it.isNotBlank() }
            ?: file.originalFilename?.substringAfterLast('/')?.substringAfterLast('\\')
            ?: return ResponseEntity.badRequest().body(ErrorResponse("File name is required"))

        val stored = file.inputStream.use { input ->
            fileService.upload(
                ownerId = user.userId,
                tenantId = user.tenantId,
                parentId = parentId,
                name = effectiveName,
                contentType = file.contentType,
                size = file.size,
                content = input,
            )
        }
        return ResponseEntity.ok(FileMetadataResponse.from(stored))
    }

    @GetMapping("/{id}/content")
    @Operation(
        summary = "Download a file's content",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "File bytes streamed"),
            ApiResponse(responseCode = "400", description = "Target is a folder"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "File not found"),
            ApiResponse(responseCode = "503", description = "Storage provider not available"),
        ],
    )
    fun download(@PathVariable id: UUID, request: HttpServletRequest): ResponseEntity<StreamingResponseBody> {
        val user = currentUser.resolve(request)
            ?: return ResponseEntity.status(401).build()
        val (file, stream) = fileService.openForDownload(user.userId, id)
        val body = StreamingResponseBody { output ->
            stream.use { it.copyTo(output) }
        }
        val encoded = URLEncoder.encode(file.name, StandardCharsets.UTF_8).replace("+", "%20")
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE))
            .contentLength(file.sizeBytes)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''$encoded")
            .body(body)
    }

    // --- Folders ------------------------------------------------------------

    @PostMapping("/folder", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Create a folder",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Folder created"),
            ApiResponse(responseCode = "400", description = "Invalid name or parent"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Parent folder not found"),
            ApiResponse(responseCode = "409", description = "A node with that name already exists"),
        ],
    )
    fun createFolder(
        @RequestBody body: CreateFolderRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        val folder = fileService.createFolder(
            ownerId = user.userId,
            tenantId = user.tenantId,
            parentId = body.parentId,
            name = body.name,
        )
        return ResponseEntity.ok(FileMetadataResponse.from(folder))
    }

    // --- Rename / move / delete --------------------------------------------

    @PatchMapping("/{id}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Rename and/or move a node",
        description = "Optional `name` renames; optional `parentId` moves. Set `moveToRoot=true` " +
            "to move to the root (because `parentId=null` is ambiguous in JSON).",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Updated"),
            ApiResponse(responseCode = "400", description = "Invalid target"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Node or target parent not found"),
            ApiResponse(responseCode = "409", description = "Name collision at the target"),
        ],
    )
    fun update(
        @PathVariable id: UUID,
        @RequestBody body: UpdateFileRequest,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        val updated = fileService.update(
            ownerId = user.userId,
            id = id,
            newName = body.name,
            newParentId = body.parentId,
            moveToRoot = body.moveToRoot,
        )
        return ResponseEntity.ok(FileMetadataResponse.from(updated))
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete a node (folders are deleted recursively)",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Deleted"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "Node not found"),
        ],
    )
    fun delete(@PathVariable id: UUID, request: HttpServletRequest): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        fileService.delete(user.userId, id)
        return ResponseEntity.noContent().build()
    }

    // --- Error mapping ------------------------------------------------------

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(401).body(ErrorResponse("Missing or invalid bearer token"))

    @ExceptionHandler(NotFoundException::class)
    fun onNotFound(e: NotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(404).body(ErrorResponse(e.message ?: "Not found"))

    @ExceptionHandler(BadRequestException::class)
    fun onBadRequest(e: BadRequestException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(e.message ?: "Bad request"))

    @ExceptionHandler(ConflictException::class)
    fun onConflict(e: ConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(409).body(ErrorResponse(e.message ?: "Conflict"))

    @ExceptionHandler(PayloadTooLargeException::class)
    fun onTooLarge(e: PayloadTooLargeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(413).body(ErrorResponse(e.message ?: "Payload too large"))

    @ExceptionHandler(ServiceUnavailableException::class)
    fun onUnavailable(e: ServiceUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(503).body(ErrorResponse(e.message ?: "Service unavailable"))
}
