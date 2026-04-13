package cloud.fiely.plugin

import org.pf4j.ExtensionPoint
import java.io.InputStream

/**
 * Extension point for file post-processing after upload.
 *
 * Multiple processors can be active simultaneously. They run in [priority] order
 * (lower values run first). Use cases: thumbnail generation, text extraction,
 * virus scanning, EXIF extraction.
 */
interface FileProcessor : ExtensionPoint {
    val id: String
    val supportedMimeTypes: Set<String>

    fun process(context: FileProcessingContext): FileProcessingResult
    fun priority(): Int = 0
}

data class FileProcessingContext(
    val fileRef: FileReference,
    val metadata: FileMetadata,
    val inputStream: InputStream
)

data class FileMetadata(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val ownerId: String
)

sealed class FileProcessingResult {
    data class Success(val outputs: Map<String, Any> = emptyMap()) : FileProcessingResult()
    data class Skip(val reason: String) : FileProcessingResult()
    data class Error(val message: String) : FileProcessingResult()
}
