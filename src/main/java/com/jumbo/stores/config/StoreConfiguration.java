package com.jumbo.stores.config;

import com.jumbo.stores.repository.JsonStoreRepository;
import com.jumbo.stores.repository.StoreRepository;
import com.jumbo.stores.service.DistanceCalculator;
import com.jumbo.stores.service.HaversineDistance;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StoreDataProperties.class)
public class StoreConfiguration {
    @Bean
    public DistanceCalculator haversineDistance() {
        return new HaversineDistance();
    }

    @Bean
    public StoreRepository storeRepository(StoreDataProperties properties) {
        return new JsonStoreRepository(properties.location());
    }
}
