package com.commonsengine.platform.exception

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 全局异常处理器（#63）
 *
 * 统一拦截所有 Controller 层异常，返回结构化的错误 JSON：
 *
 * ```json
 * {
 *   "error": "BAD_REQUEST",
 *   "code": null,
 *   "message": "交易必须为 CHARGED 状态才能分账，当前: REFUNDED"
 * }
 * ```
 *
 * 映射规则：
 * - BusinessRuleException → 422 Unprocessable Entity
 * - NotFoundException → 404 Not Found
 * - IllegalArgumentException → 400 Bad Request
 * - MethodArgumentNotValidException → 400 Bad Request（校验失败详情）
 * - MethodArgumentTypeMismatchException → 400 Bad Request
 * - 其他未捕获异常 → 500 Internal Server Error（不泄漏堆栈）
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * 统一错误响应体
     */
    data class ErrorResponse(
        val error: String,
        val code: String? = null,
        val message: String,
    )

    companion object {
        private fun badRequest(message: String, code: String? = null) =
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse("BAD_REQUEST", code, message))

        private fun unprocessable(message: String, code: String?) =
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse("UNPROCESSABLE_ENTITY", code, message))

        private fun notFound(message: String) =
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("NOT_FOUND", "NOT_FOUND", message))

        private fun internal(message: String) =
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse("INTERNAL_ERROR", null, message))
    }

    /**
     * 业务规则违反 → 422
     */
    @ExceptionHandler(BusinessRuleException::class)
    fun handleBusinessRule(ex: BusinessRuleException): ResponseEntity<ErrorResponse> {
        return unprocessable(ex.message, ex.code)
    }

    /**
     * 资源不存在 → 404
     */
    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<ErrorResponse> {
        return notFound(ex.message)
    }

    /**
     * 参数校验失败（require{} 抛出）→ 400
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.debug("Bad request: {}", ex.message)
        return badRequest(ex.message ?: "请求参数无效")
    }

    /**
     * 请求体校验失败（@Valid / @NotNull 等）→ 400
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage}"
        }
        logger.debug("Validation failed: {}", errors)
        return badRequest(errors.ifBlank { "请求参数校验失败" })
    }

    /**
     * 请求体不可读（JSON 格式错误 / 缺失必填字段）→ 400
     *
     * 当请求 DTO 标注了 @NotNull 等约束但请求体完全缺失，
     * 或 JSON 结构错误（如缺少必填字段导致反序列化失败），
     * Spring 会抛此异常而非 MethodArgumentNotValidException。
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.debug("Request body not readable: {}", ex.message)
        val message = ex.mostSpecificCause?.message ?: "请求体格式错误或缺失必填字段"
        return badRequest(message)
    }

    /**
     * 路径/查询参数类型转换失败 → 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.debug("Type mismatch: {}", ex.message)
        return badRequest("参数类型转换失败：${ex.name} 无法转换为 ${ex.requiredType?.simpleName ?: "目标类型"}")
    }

    /**
     * 兜底：所有其他未捕获异常 → 500（不泄漏堆栈）
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("未预期的异常", ex)
        return internal("服务器内部错误，请稍后重试")
    }
}
