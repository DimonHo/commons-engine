package com.commonsengine.platform.support;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java 友好的枚举解析工具——与 Kotlin {@code Enums} 对象等价。
 *
 * <p>非法枚举值抛出带候选值清单的 {@link IllegalArgumentException}，
 * 由 {@code GlobalExceptionHandler} 统一映射为 HTTP 400 Bad Request。
 *
 * <p>本类供从 Kotlin 迁移到 Java 的模块使用（Kotlin 内联 reified 函数
 * 对 Java 调用方不友好，这里提供等价的静态方法）。
 */
public final class EnumParser {

    private EnumParser() {
    }

    /**
     * 解析枚举。非法值抛出 {@link IllegalArgumentException}（→ 400）。
     *
     * @param value 字符串值
     * @param enumType 枚举 Class 对象
     * @return 解析得到的枚举常量
     * @throws IllegalArgumentException 值不匹配任何枚举常量
     */
    public static <T extends Enum<T>> T parse(String value, Class<T> enumType) {
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ex) {
            String candidates = java.util.Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "无效的 " + enumType.getSimpleName() + " 值: '" + value + "'。合法值: " + candidates);
        }
    }

    /**
     * 批量解析枚举列表。任一非法值即抛出 {@link IllegalArgumentException}（→ 400）。
     *
     * @param values 字符串集合
     * @param enumType 枚举 Class 对象
     * @return 解析得到的枚举集合（保持插入顺序）
     */
    public static <T extends Enum<T>> Set<T> parseAll(Collection<String> values, Class<T> enumType) {
        Set<T> result = new LinkedHashSet<>();
        for (String v : values) {
            result.add(parse(v, enumType));
        }
        return result;
    }
}
