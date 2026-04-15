package cloud.fiely.file.web

import cloud.fiely.auth.web.ErrorResponse
import cloud.fiely.file.service.BadRequestException
import cloud.fiely.file.service.ConflictException
import cloud.fiely.file.service.NotFoundException
import cloud.fiely.file.service.PayloadTooLargeException
import cloud.fiely.file.service.ServiceUnavailableException
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Maps the service-layer exceptions thrown by the file domain to HTTP
 * statuses. Scoped to `cloud.fiely.file.web` so it doesn't accidentally
 * shadow behaviour for other controllers.
 */
@ControllerAdvice(basePackages = ["cloud.fiely.file.web"])
@Order(0)
class FileExceptionHandler {

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
