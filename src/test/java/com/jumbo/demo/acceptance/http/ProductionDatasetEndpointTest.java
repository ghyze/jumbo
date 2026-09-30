package com.jumbo.demo.acceptance.http;

import static com.jumbo.demo.acceptance.HttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jumbo.demo.DemoApplication;
import com.jumbo.demo.repository.StoreRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionDatasetEndpointTest {
    @Autowired
    StoreRepository storeRepository;

    @LocalServerPort
    int port;

    @Test
    void packagedProductionSnapshotLoadsAll587StoresAndServesFiveOverHttp() {
        assertEquals(587, storeRepository.findAll().size());
        assertSuccess(search(port, Map.of("latitude", "52.0907", "longitude", "5.1214")), 5);
    }
}
