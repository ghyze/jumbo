package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.acceptance.FixtureServer;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.search.max-results=10")
class ConfiguredNearestStoresEndpointTest extends FixtureServer {
    @ParameterizedTest(name = "configured cap 10, limit={0}")
    @MethodSource("limits")
    void configuredCountControlsDefaultAndCap(String limit, int count) {
        var query = new LinkedHashMap<String, String>();
        query.put("latitude", "52");
        query.put("longitude", "5");
        if (limit != null) {
            query.put("limit", limit);
        }
        Response response = search(port, query);
        assertSuccess(response, count);
        assertFixtureOrder(response, count);
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of(null, 10), Arguments.of("10", 10),
                Arguments.of("20", 10), Arguments.of("", 10));
    }
}
