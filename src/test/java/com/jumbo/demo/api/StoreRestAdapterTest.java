package com.jumbo.demo.api;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.demo.config.SearchProperties;
import com.jumbo.demo.domain.Store;
import com.jumbo.demo.service.HaversineDistance;
import com.jumbo.demo.service.StoreService;
import com.jumbo.demo.support.TestObjects;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class StoreRestAdapterTest {
    @Test
    void mapsEveryFieldWithoutRoundingOrCombiningAddressComponents() {
        var store = TestObjects.store().id("distinct-id").addressName("Distinct address").city("Distinct city")
                .postalCode("1234 AB").street("Distinct street").street2("42").street3("Rear entrance")
                .coordinates(TestObjects.coordinates().latitude(51.234567).longitude(6.765432).build()).build();
        var response = StoreRestAdapter.toResponse(TestObjects.nearestStore()
                .store(store).distanceKm(12.3456789012345).build());
        assertAll(
                () -> assertEquals("distinct-id", response.getId()),
                () -> assertEquals("Distinct address", response.getAddressName()),
                () -> assertEquals("Distinct city", response.getCity()),
                () -> assertEquals("1234 AB", response.getPostalCode()),
                () -> assertEquals("Distinct street", response.getStreet()),
                () -> assertEquals("42", response.getStreet2()),
                () -> assertEquals("Rear entrance", response.getStreet3()),
                () -> assertEquals(51.234567, response.getLatitude()),
                () -> assertEquals(6.765432, response.getLongitude()),
                () -> assertEquals(12.3456789012345, response.getDistanceKm()));
    }

    @Test
    void preservesNormalizedOptionalAddressFields() {
        var nearest = TestObjects.nearestStore()
                .store(TestObjects.store().street2(null).street3(null).build()).build();
        var response = StoreRestAdapter.toResponse(nearest);
        assertEquals("", response.getStreet2());
        assertEquals("", response.getStreet3());
    }

    @Test
    void returnsJsonWithConfiguredDefaultAndServiceOrdering() {
        var adapter = adapter(3, stores(5));
        var response = adapter.findNearestStores(52.0907, 5.1214, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(List.of("store-0", "store-1", "store-2"),
                body.getStores().stream().map(store -> store.getId()).toList());
        assertTrue(body.getWarnings().isEmpty());
        assertEquals(0.0, body.getStores().getFirst().getDistanceKm());
    }

    @ParameterizedTest
    @CsvSource({"2, 2, 0", "20, 4, 0", "invalid, 4, 1", "0, 4, 1"})
    void resolvesLimitsBeforeCallingRealService(String limit, int expectedCount, int expectedWarnings) {
        var body = adapter(4, stores(6)).findNearestStores(52.0907, 5.1214, limit).getBody();
        assertNotNull(body);
        assertEquals(expectedCount, body.getStores().size());
        assertEquals(expectedWarnings, body.getWarnings().size());
        if (expectedWarnings > 0) {
            assertEquals("INVALID_LIMIT_DEFAULTED", body.getWarnings().getFirst().getCode());
            assertTrue(body.getWarnings().getFirst().getMessage().contains("configured count of 4"));
        }
    }

    @Test
    void composesLimitAndServiceWarningsPreservingServiceCodeAndMessage() {
        var properties = new SearchProperties(5);
        var service = new StoreService(() -> stores(2), properties, new HaversineDistance());
        var coordinates = TestObjects.coordinates().latitude(0).longitude(0).build();
        var serviceWarning = service.findNearest(coordinates, 5).warnings().getFirst();
        var body = new StoreRestAdapter(service, properties).findNearestStores(0.0, 0.0, "bad").getBody();
        assertNotNull(body);
        assertEquals(2, body.getStores().size());
        assertEquals(2, body.getWarnings().size());
        assertEquals("INVALID_LIMIT_DEFAULTED", body.getWarnings().getFirst().getCode());
        assertEquals(serviceWarning.code(), body.getWarnings().get(1).getCode());
        assertEquals(serviceWarning.message(), body.getWarnings().get(1).getMessage());
    }

    @Test
    void emptyRepositoryReturnsEmptyArrays() {
        var body = adapter(5, List.of()).findNearestStores(52.0, 5.0, null).getBody();
        assertNotNull(body);
        assertTrue(body.getStores().isEmpty());
        assertTrue(body.getWarnings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"NaN, 5", "Infinity, 5", "-Infinity, 5", "91, 5", "-91, 5",
            "52, NaN", "52, Infinity", "52, -Infinity", "52, 181", "52, -181"})
    void domainCoordinateFailuresBecomeSpecificInputErrors(double latitude, double longitude) {
        assertThrows(StoreRestAdapter.InvalidCoordinatesException.class,
                () -> adapter(5, stores(1)).findNearestStores(latitude, longitude, null));
    }

    @Test
    void absentCoordinatesBecomeSpecificInputErrorsEvenWithoutMvcValidation() {
        var adapter = adapter(5, stores(1));
        assertThrows(StoreRestAdapter.InvalidCoordinatesException.class,
                () -> adapter.findNearestStores(null, 5.0, null));
        assertThrows(StoreRestAdapter.InvalidCoordinatesException.class,
                () -> adapter.findNearestStores(52.0, null, null));
    }

    @Test
    void unrelatedIllegalArgumentsRemainServerFailures() {
        var failure = new IllegalArgumentException("internal repository failure");
        var properties = new SearchProperties(5);
        var service = new StoreService(() -> { throw failure; }, properties, new HaversineDistance());
        var adapter = new StoreRestAdapter(service, properties);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> adapter.findNearestStores(52.0, 5.0, null)));
    }

    private static StoreRestAdapter adapter(int maximum, List<Store> stores) {
        var properties = new SearchProperties(maximum);
        return new StoreRestAdapter(new StoreService(() -> stores, properties, new HaversineDistance()), properties);
    }

    private static List<Store> stores(int count) {
        return IntStream.range(0, count).mapToObj(index -> TestObjects.store().id("store-" + index).build())
                .toList();
    }
}
