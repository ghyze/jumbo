package com.jumbo.demo.acceptance.http;

import static com.jumbo.demo.acceptance.HttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.demo.DemoApplication;
import com.jumbo.demo.acceptance.FixtureServer;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.search.max-results=10")
class ConfiguredNearestStoresEndpointTest extends FixtureServer {
    @ParameterizedTest(name = "configured cap 10, limit={0}")
    @MethodSource("limits")
    void configuredCountControlsDefaultCapAndFallback(String limit, int count, boolean invalid) {
        var query = new LinkedHashMap<String, String>();
        query.put("latitude", "52");
        query.put("longitude", "5");
        if (limit != null) {
            query.put("limit", limit);
        }
        Response response = search(port, query);
        assertSuccess(response, count, invalid ? new String[]{"INVALID_LIMIT_DEFAULTED"} : new String[0]);
        assertFixtureOrder(response, count);
        if (invalid) {
            assertTrue(response.jsonPath().getString("warnings[0].message").contains("10"));
        }
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of(null, 10, false), Arguments.of("3", 3, false),
                Arguments.of("10", 10, false), Arguments.of("20", 10, false),
                Arguments.of("9".repeat(500), 10, false), Arguments.of("", 10, true),
                Arguments.of("many", 10, true));
    }
}
