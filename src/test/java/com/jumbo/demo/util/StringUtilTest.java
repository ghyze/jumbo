package com.jumbo.demo.util;

import static com.jumbo.demo.util.StringUtil.isBlank;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StringUtilTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\r\n", " \t\r\n ", "\u2003", "\u3000"})
    void recognizesNullEmptyAndWhitespace(String value) {
        assertTrue(isBlank(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", " text ", "\ttext\n", "0", "\u00a0", "\u200b"})
    void rejectsStringsContainingNonWhitespace(String value) {
        assertFalse(isBlank(value));
    }
}
