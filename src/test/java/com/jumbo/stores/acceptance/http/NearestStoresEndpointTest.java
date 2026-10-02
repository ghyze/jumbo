package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.NearestStoresApi.*;
import com.jumbo.stores.NearestStoresApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.data.location=classpath:fixtures/nearest-stores.json")
class NearestStoresEndpointTest {
    @LocalServerPort
    int port;

    @ParameterizedTest(name = "invalid {0}={1}")
    @MethodSource("invalidCoordinates")
    void rejectsInvalidCoordinatesWithConsistentProblems(String coordinate, String value) {
        var query = new LinkedHashMap<String, String>();
        query.put("latitude", "52");
        query.put("longitude", "5");
        if (value == null) {
            query.remove(coordinate);
        } else {
            query.put(coordinate, value);
        }
        assertBadParameter(search(port, query), coordinate);
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.of(
                Arguments.of("latitude", null),
                Arguments.of("latitude", ""),
                Arguments.of("latitude", "north"),
                Arguments.of("latitude", "NaN"),
                Arguments.of("latitude", "90.000001"),
                Arguments.of("longitude", " "),
                Arguments.of("longitude", "1,2"),
                Arguments.of("longitude", "Infinity"),
                Arguments.of("longitude", "-180.000001"));
    }

    @Test
    void rejectsBothMissingCoordinates() {
        assertBadParameter(search(port, Map.of()), "latitude");
    }

    @ParameterizedTest
    @CsvSource({"52, 5", "0, 0", "-90, -180", "90, 180", "-90, 180", "90, -180"})
    void acceptsInclusiveGlobalBoundsAndSearchesOutsideCoverage(double latitude, double longitude) {
        assertSuccess(search(port, Map.of("latitude", latitude, "longitude", longitude)), 5);
    }
}
