package com.jumbo.stores.acceptance;

import com.jumbo.stores.repository.JsonStoreRepository;
import com.jumbo.stores.repository.StoreRepository;
import org.springframework.core.io.ClassPathResource;

public final class FixtureRepositories {
    private FixtureRepositories() {
    }

    public static StoreRepository twelveStores() {
        return new JsonStoreRepository(new ClassPathResource("fixtures/nearest-stores.json"));
    }

    public static StoreRepository threeStores() {
        return new JsonStoreRepository(new ClassPathResource("fixtures/three-stores.json"));
    }
}
