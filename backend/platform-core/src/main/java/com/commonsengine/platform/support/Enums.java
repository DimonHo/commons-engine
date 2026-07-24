package com.commonsengine.platform.support;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Enum parsing utilities.
 */
public final class Enums {

    private Enums() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Parse a single enum constant, throwing with a helpful list of valid
     * candidates on invalid input.
     */
    public static <T extends Enum<T>> T parse(Class<T> enumType, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot parse blank value into enum " + enumType.getSimpleName());
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            String candidates = java.util.Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Invalid " + enumType.getSimpleName() + " value: '" + value
                            + "'. Valid values: " + candidates);
        }
    }

    /**
     * Parse a collection of enum constant names into a {@link Set}, preserving
     * insertion order. Duplicate or invalid entries throw.
     */
    public static <T extends Enum<T>> Set<T> parseAll(Class<T> enumType, Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<T> result = new LinkedHashSet<>();
        for (String v : values) {
            result.add(parse(enumType, v));
        }
        return Set.copyOf(result);
    }
}
