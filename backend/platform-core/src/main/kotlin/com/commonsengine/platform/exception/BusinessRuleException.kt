package com.commonsengine.platform.exception

/**
 * 业务规则异常——表示请求本身合法但违反业务规则。
 *
 * 例如：交易状态不允许分账、提案讨论期未满不能投票、纠纷工单状态不允许仲裁。
 *
 * 映射为 HTTP 422 Unprocessable Entity。
 *
 * 与 IllegalArgumentException（映射为 400）的区别：
 * - 400 Bad Request：请求格式或参数有误（如空值、类型错误）
 * - 422 Unprocessable Entity：请求格式正确，但业务规则不允许执行
 *
 * @param code 机器可读的错误代码，如 "TRANSACTION_NOT_CHARGED"
 * @param message 人类可读的错误描述
 */
open class BusinessRuleException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 资源不存在异常——请求的资源未找到。
 *
 * 映射为 HTTP 404 Not Found。
 */
class NotFoundException(
    val resourceType: String,
    val resourceId: String,
) : BusinessRuleException(
    code = "NOT_FOUND",
    message = "$resourceType 不存在: $resourceId",
)
