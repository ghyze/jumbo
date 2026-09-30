package com.jumbo.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jumbo.demo.repository.StoreRepository;
import com.jumbo.demo.service.StoreService;
import com.jumbo.demo.support.TestObjects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StoreConfigurationTest {
    @Test
    void productionWiringLoadsClasspathSeedAndServesNearestFive() {
        new ApplicationContextRunner().withUserConfiguration(StoreConfiguration.class, StoreService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
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
}
