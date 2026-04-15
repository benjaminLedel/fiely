package cloud.fiely.file.web

import cloud.fiely.auth.web.CurrentUserResolver
import cloud.fiely.auth.web.ErrorResponse
import cloud.fiely.file.service.FileMetadataService
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Namespaced metadata endpoints attached to files.
 *
 * Each namespace holds a single JSON document. The `user` namespace is the
 * usual home for caller-supplied tags/ratings/notes; extractor plugins and AI
 * processors pick their own (typically their plugin id) so writes don't
 * collide across producers.
 */
@RestController
@RequestMapping("/api/files/{fileId}/metadata")
@Tag(name = "Files", description = "Namespaced metadata attached to files.")
class FileMetadataController(
    private val service: FileMetadataService,
    private val currentUser: CurrentUserResolver,
) {

    @GetMapping
    @Operation(
        summary = "List all metadata namespaces for a file",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Metadata listed"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "File not found"),
        ],
    )
    fun list(
        @PathVariable fileId: UUID,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        return ResponseEntity.ok(service.list(user.userId, fileId))
    }

    @GetMapping("/{namespace}")
    @Operation(
        summary = "Get the metadata document for a single namespace",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Metadata returned"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "File or namespace not found"),
        ],
    )
    fun get(
        @PathVariable fileId: UUID,
        @PathVariable namespace: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        return ResponseEntity.ok(service.get(user.userId, fileId, namespace))
    }

    @PutMapping("/{namespace}", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Upsert the metadata document for a namespace",
        description = "The request body is stored verbatim as the namespace's current document.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Metadata stored"),
            ApiResponse(responseCode = "400", description = "Invalid namespace or body"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "File not found"),
        ],
    )
    fun put(
        @PathVariable fileId: UUID,
        @PathVariable namespace: String,
        @RequestBody body: JsonNode,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        return ResponseEntity.ok(service.put(user.userId, fileId, namespace, body))
    }

    @DeleteMapping("/{namespace}")
    @Operation(
        summary = "Remove a namespace's metadata document",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Removed"),
            ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            ApiResponse(responseCode = "404", description = "File or namespace not found"),
        ],
    )
    fun delete(
        @PathVariable fileId: UUID,
        @PathVariable namespace: String,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser.resolve(request) ?: return unauthorized()
        service.delete(user.userId, fileId, namespace)
        return ResponseEntity.noContent().build()
    }

    private fun unauthorized(): ResponseEntity<Any> =
        ResponseEntity.status(401).body(ErrorResponse("Missing or invalid bearer token"))
}
