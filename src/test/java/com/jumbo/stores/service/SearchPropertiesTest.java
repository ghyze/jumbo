package com.jumbo.stores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SearchPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsToFiveOnlyWhenOmitted() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertEquals(5, context.getBean(SearchProperties.class).maxResults());
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, Integer.MAX_VALUE})
    void allowsPositiveOverride(int maximum) {
        contextRunner.withPropertyValues("stores.search.max-results=" + maximum).run(context -> {
            assertThat(context).hasNotFailed();
            assertEquals(maximum, context.getBean(SearchProperties.class).maxResults());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void failsStartupForExplicitBlankValue(String value) {
        contextRunner.withPropertyValues("stores.search.max-results=" + value).run(context ->
                assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no", "1.5", "0", "-1", "2147483648", "999999999999999999999", "NaN"})
    void failsStartupForExplicitInvalidValue(String value) {
        contextRunner.withPropertyValues("stores.search.max-results=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("stores.search.max-results");
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void directConstructionAlsoRejectsNonpositiveMaximum(int maximum) {
        assertThrows(IllegalArgumentException.class, () -> new SearchProperties(maximum));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SearchProperties.class)
    static class PropertiesConfiguration {
    }
}
