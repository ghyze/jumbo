package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.acceptance.FixtureServer;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NearestStoresEndpointTest extends FixtureServer {
    @ParameterizedTest(name = "limit={0}, count={1}")
    @MethodSource("limits")
    void resolvesValidLimitsWithoutChangingSuccessContentType(String limit, int count) {
        Response response = search(port, Map.of("latitude", "52", "longitude", "5", "limit", limit));
        assertSuccess(response, count);
        assertFixtureOrder(response, count);
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("1", 1), Arguments.of("5", 5),
                Arguments.of("", 5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2.5", "2147483648"})
    void rejectsInvalidLimits(String limit) {
        assertBadLimit(search(port, Map.of("latitude", "52", "longitude", "5", "limit", limit)));
    }

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
        assertBadCoordinates(search(port, query), coordinate);
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
        assertBadCoordinates(search(port, Map.of()), "latitude");
    }

    @ParameterizedTest
    @CsvSource({
            "50.7, 3.2, false", "50.7, 7.3, false", "53.6, 3.2, false", "53.6, 7.3, false",
            "50.700001, 5, false", "53.599999, 5, false", "52, 3.200001, false", "52, 7.299999, false",
            "50.699999, 5, true", "53.600001, 5, true", "52, 3.199999, true", "52, 7.300001, true",
            "-90, -180, true", "90, 180, true", "-90, 180, true", "90, -180, true"
    })
    void distinguishesInclusiveGlobalValidityFromInclusiveCoverage(double latitude, double longitude,
                                                                   boolean outside) {
        Response response = search(port, Map.of("latitude", latitude, "longitude", longitude));
        assertSuccess(response, 5, outside ? new String[]{"OUTSIDE_SUPPORTED_AREA"} : new String[0]);
    }
}
