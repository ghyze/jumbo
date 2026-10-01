package com.jumbo.demo.config;

import com.jumbo.demo.repository.JsonStoreRepository;
import com.jumbo.demo.repository.StoreRepository;
import com.jumbo.demo.service.DistanceCalculator;
import com.jumbo.demo.service.HaversineDistance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchProperties.class)
public class StoreConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "stores.distance", name = "algorithm",
            havingValue = "haversine", matchIfMissing = true)
    public DistanceCalculator haversineDistance() {
        return new HaversineDistance();
    }

    @Bean
    public StoreRepository storeRepository() {
        return new JsonStoreRepository(new ClassPathResource("stores.json"));
    }
}
