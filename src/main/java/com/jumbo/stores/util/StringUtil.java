package com.jumbo.stores.util;

public final class StringUtil {
    private StringUtil() {
    }

    /** Returns true for null, empty, or whitespace-only strings, using {@link String#isBlank()} semantics. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
