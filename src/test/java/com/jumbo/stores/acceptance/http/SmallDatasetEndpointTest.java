package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.repository.StoreRepository;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmallDatasetEndpointTest {
    @TestBean(name = "storeRepository", enforceOverride = true,
            methodName = "com.jumbo.stores.acceptance.FixtureRepositories#threeStores")
    StoreRepository storeRepository;

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "three available stores, limit={0}")
    @MethodSource("limits")
    void returnsOnlyAvailableStores(String limit, int count) {
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
        return Stream.of(Arguments.of(null, 3), Arguments.of("2", 2),
                Arguments.of("20", 3), Arguments.of("", 3));
    }
}
