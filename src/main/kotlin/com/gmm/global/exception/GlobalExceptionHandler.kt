package com.gmm.global.exception

import com.gmm.global.config.GmmProperties
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler(private val properties: GmmProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(GmmException::class)
    fun handleGmm(e: GmmException): ResponseEntity<ErrorResponse> = respond(e.code, e.message)

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ErrorResponse> = respond(
        ErrorCode.FILE_TOO_LARGE,
        "파일 용량이 너무 커요. 최대 ${properties.build.maxZipSize.toMegabytes()}MB까지 올릴 수 있어요.",
    )

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(e: MissingServletRequestPartException): ResponseEntity<ErrorResponse> =
        when (e.requestPartName) {
            "buildFile" -> respond(ErrorCode.BUILD_REQUIRED)
            "file" -> respond(ErrorCode.IMAGE_REQUIRED)
            else -> respond(ErrorCode.INVALID_REQUEST)
        }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        MultipartException::class,
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ErrorResponse> {
        log.debug("Reject malformed request: {}", e.message)
        return respond(ErrorCode.INVALID_REQUEST)
    }

    /** A unique constraint lost a race, e.g. two first reviews of one game sent at once. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(e: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("Reject conflicting write: {}", e.mostSpecificCause.message)
        return respond(ErrorCode.CONFLICT)
    }

    /** The row changed or was deleted by another request in the meantime, e.g. a game edited while it is deleted. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleStaleWrite(e: OptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        log.warn("Reject stale write: {}", e.message)
        return respond(ErrorCode.CONFLICT)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = respond(ErrorCode.NOT_FOUND)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethod(e: HttpRequestMethodNotSupportedException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.METHOD_NOT_ALLOWED)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaType(e: HttpMediaTypeNotSupportedException): ResponseEntity<ErrorResponse> =
        respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Handle unexpected error", e)
        return respond(ErrorCode.INTERNAL_ERROR)
    }

    private fun respond(code: ErrorCode, message: String = code.message): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(code.status).body(ErrorResponse(code.name, message))
}
