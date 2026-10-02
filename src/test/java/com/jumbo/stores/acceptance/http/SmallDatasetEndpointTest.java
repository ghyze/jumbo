package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import com.jumbo.stores.NearestStoresApplication;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.data.location=classpath:fixtures/three-stores.json")
class SmallDatasetEndpointTest {
    @LocalServerPort
    int port;

    @Test
    void returnsOnlyAvailableStores() {
        Response response = search(port, Map.of("latitude", "52", "longitude", "5"));
        assertSuccess(response, 3);
        assertFixtureOrder(response, 3);
    }
}
