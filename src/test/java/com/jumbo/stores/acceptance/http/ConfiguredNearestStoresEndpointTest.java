package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.acceptance.FixtureServer;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.search.max-results=10")
class ConfiguredNearestStoresEndpointTest extends FixtureServer {
    @Test
    void configuredCountControlsResultCount() {
        Response response = search(port, Map.of("latitude", "52", "longitude", "5"));
        assertSuccess(response, 10);
        assertFixtureOrder(response, 10);
    }
}
