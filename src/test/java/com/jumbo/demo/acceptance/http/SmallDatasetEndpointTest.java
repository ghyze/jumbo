package com.jumbo.demo.acceptance.http;

import static com.jumbo.demo.acceptance.HttpAssertions.*;
import com.jumbo.demo.DemoApplication;
import com.jumbo.demo.repository.StoreRepository;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmallDatasetEndpointTest {
    @TestBean(name = "storeRepository", enforceOverride = true,
            methodName = "com.jumbo.demo.acceptance.FixtureRepositories#threeStores")
    StoreRepository storeRepository;

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "three available stores, limit={0}")
    @MethodSource("limits")
    void returnsOnlyAvailableStores(String limit, int count, boolean invalid) {
        var query = new LinkedHashMap<String, String>();
        query.put("latitude", "52");
        query.put("longitude", "5");
        if (limit != null) {
            query.put("limit", limit);
        }
        Response response = search(port, query);
        assertSuccess(response, count, invalid ? new String[]{"INVALID_LIMIT_DEFAULTED"} : new String[0]);
        assertFixtureOrder(response, count);
    }

    static Stream<Arguments> limits() {
        return Stream.of(Arguments.of(null, 3, false), Arguments.of("2", 2, false),
                Arguments.of("20", 3, false), Arguments.of("many", 3, true));
    }
}
