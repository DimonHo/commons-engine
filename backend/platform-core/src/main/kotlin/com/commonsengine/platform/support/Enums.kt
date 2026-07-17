package com.commonsengine.platform.support

/**
 * 统一的枚举解析工具（#67）。
 *
 * 项目中所有 Controller 的字符串→枚举转换都应通过本工具完成，
 * 保证「非法枚举值 → 400 Bad Request」的一致行为：
 *
 * - 不安全：`EnumType.valueOf(s)` — 非法值抛 `IllegalArgumentException`，
 *   被 `GlobalExceptionHandler` 捕获为 400 ✅，但错误信息不友好（仅含枚举名）。
 * - 静默丢弃：`runCatching { valueOf(s) }.getOrNull()` — 非法值被忽略 ❌
 * - 静默默认：`runCatching { valueOf(s) }.getOrDefault(DEFAULT)` — 静默改写 ❌
 *
 * 本工具统一为：非法值抛出**带候选值清单**的 `IllegalArgumentException`，
 * 由全局异常处理器映射为 400。
 */
object Enums {

    /**
     * 解析枚举。非法值抛 [IllegalArgumentException]（→ 400）。
     *
     * 用法：`Enums.parse<RatingDirection>(body.direction)`
     */
    inline fun <reified T : Enum<T>> parse(value: String): T {
        return runCatching { enumValueOf<T>(value) }.getOrElse {
            throw IllegalArgumentException(
                "无效的 ${T::class.simpleName} 值: '$value'。" +
                    "合法值: ${enumValues<T>().joinToString(", ")}"
            )
        }
    }

    /**
     * 批量解析枚举列表。任一非法值即抛 [IllegalArgumentException]（→ 400）。
     *
     * 用法：`Enums.parseAll<RatingTag>(body.tags)`
     */
    inline fun <reified T : Enum<T>> parseAll(values: Collection<String>): Set<T> =
        values.map { parse<T>(it) }.toSet()
}
