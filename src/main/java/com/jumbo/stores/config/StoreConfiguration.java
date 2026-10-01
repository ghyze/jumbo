package com.jumbo.stores.config;

import com.jumbo.stores.repository.JsonStoreRepository;
import com.jumbo.stores.repository.StoreRepository;
import com.jumbo.stores.service.DistanceCalculator;
import com.jumbo.stores.service.HaversineDistance;
import com.jumbo.stores.service.SearchProperties;
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
