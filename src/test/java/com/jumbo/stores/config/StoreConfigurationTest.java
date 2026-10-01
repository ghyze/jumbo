package com.jumbo.stores.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.stores.repository.StoreRepository;
import com.jumbo.stores.service.DistanceCalculator;
import com.jumbo.stores.service.HaversineDistance;
import com.jumbo.stores.service.StoreService;
import com.jumbo.stores.support.TestObjects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
    void missingStoreDataLocationFailsStartup() {
        runner.withPropertyValues("stores.data.location=classpath:does-not-exist.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("Failed to load stores")
                            .hasStackTraceContaining("does-not-exist.json");
                });
    }
}
