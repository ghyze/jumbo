package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.NearestStoresApi.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.repository.StoreRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionDatasetEndpointTest {
    @Autowired
    StoreRepository storeRepository;

    @LocalServerPort
    int port;

    @Test
    void packagedProductionSnapshotLoadsAll587StoresAndServesFiveOverHttp() {
        assertThat(storeRepository.findAll()).hasSize(587);
        assertSuccess(search(port, Map.of("latitude", "52.0907", "longitude", "5.1214")), 5);
    }
}
