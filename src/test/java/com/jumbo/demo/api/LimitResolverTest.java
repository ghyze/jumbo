package com.jumbo.demo.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.demo.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class LimitResolverTest {
    private final LimitResolver resolver = new LimitResolver(new SearchProperties(10));

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"10", "11", "2147483647", "2147483648", "999999999999999999999999999999999"})
    void omissionAndCappingUseConfiguredCountWithoutWarning(String input) {
        assertEquals(new LimitResolver.Resolution(10, false), resolver.resolve(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", "0", "000000", "-1", "+1", "1.0", ".5", "1e2",
            "NaN", "Infinity", "five", "1 2", "1\n2", "１２", "١٢",
            "999999999999999999999999999999999x", "999999999999999999999999999999999.0"})
    void invalidValuesUseConfiguredCountWithWarning(String input) {
        assertEquals(new LimitResolver.Resolution(10, true), resolver.resolve(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3", "0003", " 3 ", "\t003\r\n", "\u20033\u2003"})
    void acceptsPositiveDigitsWithLeadingZerosAndSurroundingWhitespace(String input) {
        assertEquals(new LimitResolver.Resolution(3, false), resolver.resolve(input));
    }

    @Test
    void longLeadingZerosDoNotChangeTheNumericValue() {
        assertEquals(new LimitResolver.Resolution(3, false), resolver.resolve("0".repeat(10_000) + "3"));
        assertEquals(new LimitResolver.Resolution(10, false), resolver.resolve("0".repeat(10_000) + "11"));
        assertTrue(resolver.resolve("0".repeat(10_000)).defaulted());
    }

    @Test
    void hugePositiveValuesCapAndStillValidateTheEntireInput() {
        assertEquals(new LimitResolver.Resolution(10, false), resolver.resolve("9".repeat(10_000)));
        assertTrue(resolver.resolve("9".repeat(10_000) + "x").defaulted());
    }

    @Test
    void handlesMaximumJavaIntegerWithoutOverflow() {
        var largest = new LimitResolver(new SearchProperties(Integer.MAX_VALUE));
        assertEquals(new LimitResolver.Resolution(Integer.MAX_VALUE - 1, false),
                largest.resolve("2147483646"));
        assertEquals(new LimitResolver.Resolution(Integer.MAX_VALUE, false),
                largest.resolve("2147483647"));
        assertEquals(new LimitResolver.Resolution(Integer.MAX_VALUE, false),
                largest.resolve("2147483648"));
        assertEquals(new LimitResolver.Resolution(Integer.MAX_VALUE, false),
                largest.resolve("99999999999999999999999999999999999999999999"));
    }

    @Test
    void smallestConfiguredCapWorks() {
        var smallest = new LimitResolver(new SearchProperties(1));
        assertEquals(1, smallest.resolve("0001").count());
        assertFalse(smallest.resolve("9999").defaulted());
        assertTrue(smallest.resolve("0").defaulted());
    }
}
