package com.jumbo.stores.acceptance.http;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.acceptance.FixtureServer;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NearestStoresEndpointTest extends FixtureServer {
    @Test
    void defaultFiveHaveEveryMappedFieldAndIndependentDistancesInTieBrokenOrder() {
        Response response = search(port, Map.of("latitude", "52", "longitude", "5"));
        assertSuccess(response, 5);
        assertFixtureOrder(response, 5);
        String[][] expectedText = {
                {"store-a", "Jumbo Shared A", "Shared A", "3512 AA", "Acacialaan", "17", "bis"},
                {"store-b", "Jumbo Shared B", "Shared B", "3512 BB", "Berkenlaan", "", ""},
                {"store-c", "Jumbo North C", "North C", "3512 CC", "Cederlaan", "3", ""},
                {"store-d", "Jumbo North D", "North D", "3512 DD", "Dennenlaan", "4", "D"},
                {"store-e", "Jumbo North E", "North E", "3512 EE", "Eikenlaan", "5", "E"}
        };
        List<String> fields = List.of("id", "addressName", "city", "postalCode", "street", "street2", "street3");
        double[] expectedLatitudes = {52, 52, 52.01, 52.02, 52.03};
        // Meridional arcs: 0.01 degree is 1.111950802335329 km for the specified mean Earth radius.
        double[] expectedDistances = {0, 0, 1.111950802335329, 2.223901604670658, 3.335852407005987};
        for (int index = 0; index < 5; index++) {
            String path = "stores[" + index + "].";
            for (int field = 0; field < fields.size(); field++) {
                assertEquals(expectedText[index][field], response.jsonPath().getString(path + fields.get(field)));
            }
            assertEquals(expectedLatitudes[index], response.jsonPath().getDouble(path + "latitude"), 1e-10);
            assertEquals(5, response.jsonPath().getDouble(path + "longitude"), 1e-10);
            assertEquals(expectedDistances[index], response.jsonPath().getDouble(path + "distanceKm"), 1e-8);
        }
    }

    @ParameterizedTest(name = "limit={0}, count={1}, warning={2}")
    @MethodSource("limits")
    void resolvesRawLimitsWithoutChangingSuccessContentType(String limit, int count, boolean invalid) {
        Response response = search(port, Map.of("latitude", "52", "longitude", "5", "limit", limit));
        assertSuccess(response, count, invalid ? new String[]{"INVALID_LIMIT_DEFAULTED"} : new String[0]);
        assertFixtureOrder(response, count);
        if (invalid) {
            assertTrue(response.jsonPath().getString("warnings[0].message").contains("5"));
        }
    }

    static Stream<Arguments> limits() {
        return Stream.of(
                Arguments.of("1", 1, false), Arguments.of("3", 3, false),
                Arguments.of("5", 5, false), Arguments.of("20", 5, false),
                Arguments.of("0003", 3, false), Arguments.of(" 2 ", 2, false),
                Arguments.of("9".repeat(500), 5, false),
                Arguments.of("0".repeat(500) + "3", 3, false),
                Arguments.of("", 5, true), Arguments.of("   ", 5, true),
                Arguments.of("many", 5, true), Arguments.of("2.5", 5, true),
                Arguments.of("0", 5, true), Arguments.of("000", 5, true),
                Arguments.of("-1", 5, true), Arguments.of("+2", 5, true),
                Arguments.of("1e2", 5, true), Arguments.of("NaN", 5, true));
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
        assertBadCoordinates(search(port, query));
    }

    static Stream<Arguments> invalidCoordinates() {
        Stream<Arguments> common = Stream.of("latitude", "longitude").flatMap(coordinate ->
                Stream.of(null, "", " ", "north", "1,2", "1.2.3", "NaN", "Infinity",
                        "-Infinity", "1e309", "-1e309")
                        .map(value -> Arguments.of(coordinate, value)));
        return Stream.concat(common, Stream.of(
                Arguments.of("latitude", "90.000001"), Arguments.of("latitude", "-90.000001"),
                Arguments.of("longitude", "180.000001"), Arguments.of("longitude", "-180.000001")));
    }

    @Test
    void rejectsBothMissingCoordinates() {
        assertBadCoordinates(search(port, Map.of()));
    }

    @ParameterizedTest
    @CsvSource({
            "50.7, 3.2, false", "50.7, 7.3, false", "53.6, 3.2, false", "53.6, 7.3, false",
            "50.700001, 5, false", "53.599999, 5, false", "52, 3.200001, false", "52, 7.299999, false",
            "50.699999, 5, true", "53.600001, 5, true", "52, 3.199999, true", "52, 7.300001, true",
            "0, 0, true", "-90, -180, true", "90, 180, true", "-90, 180, true", "90, -180, true"
    })
    void distinguishesInclusiveGlobalValidityFromInclusiveCoverage(double latitude, double longitude,
                                                                   boolean outside) {
        Response response = search(port, Map.of("latitude", latitude, "longitude", longitude));
        assertSuccess(response, 5, outside ? new String[]{"OUTSIDE_SUPPORTED_AREA"} : new String[0]);
    }

    @Test
    void combinesLimitAndCoverageWarningsWithoutLosingResults() {
        Response response = search(port, Map.of("latitude", "0", "longitude", "0", "limit", "many"));
        assertSuccess(response, 5, "INVALID_LIMIT_DEFAULTED", "OUTSIDE_SUPPORTED_AREA");
        List<String> messages = response.jsonPath().getList("warnings.message");
        assertTrue(messages.stream().anyMatch(message -> message.contains("5")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Netherlands")));
    }
}
