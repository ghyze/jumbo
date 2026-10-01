package com.jumbo.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jumbo.demo.repository.StoreRepository;
import com.jumbo.demo.service.DistanceCalculator;
import com.jumbo.demo.service.HaversineDistance;
import com.jumbo.demo.service.StoreService;
import com.jumbo.demo.support.TestObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

class StoreConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StoreConfiguration.class, StoreService.class);

    @Test
    void productionWiringLoadsClasspathSeedAndServesNearestFive() {
        runner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DistanceCalculator.class);
                    assertThat(context.getBean(DistanceCalculator.class)).isInstanceOf(HaversineDistance.class);
                    assertEquals(587, context.getBean(StoreRepository.class).findAll().size());
                    var result = context.getBean(StoreService.class)
                            .findNearest(TestObjects.coordinates().build(), 5);
                    assertEquals(5, result.stores().size());
                    assertTrue(result.warnings().isEmpty());
                    for (int index = 1; index < result.stores().size(); index++) {
                        assertTrue(result.stores().get(index - 1).distanceKm()
                                <= result.stores().get(index).distanceKm());
                    }
                });
    }

    @Test
    void explicitlySelectsHaversineWithAnotherImplementationAvailable() {
        runner.withUserConfiguration(AlternativeDistanceConfiguration.class)
                .withPropertyValues("stores.distance.algorithm=haversine")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DistanceCalculator.class);
                    assertThat(context.getBean(DistanceCalculator.class)).isInstanceOf(HaversineDistance.class);
                });
    }

    @Test
    void selectsAlternativeImplementationAndInjectsItIntoService() {
        runner.withUserConfiguration(AlternativeDistanceConfiguration.class)
                .withPropertyValues("stores.distance.algorithm=test")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DistanceCalculator.class);
                    assertThat(context).doesNotHaveBean("haversineDistance");
                    var result = context.getBean(StoreService.class)
                            .findNearest(TestObjects.coordinates().build(), 5);
                    assertEquals(5, result.stores().size());
                    assertTrue(result.stores().stream().allMatch(store -> store.distanceKm() == 42));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", ""})
    void unsupportedAlgorithmFailsStartupInsteadOfFallingBack(String algorithm) {
        runner.withPropertyValues("stores.distance.algorithm=" + algorithm)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasStackTraceContaining(DistanceCalculator.class.getName());
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AlternativeDistanceConfiguration {
        @Bean
        @ConditionalOnProperty(prefix = "stores.distance", name = "algorithm", havingValue = "test")
        DistanceCalculator testDistance() {
            return (from, to) -> 42;
        }
    }
}
