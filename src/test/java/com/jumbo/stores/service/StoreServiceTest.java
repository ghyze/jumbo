package com.jumbo.stores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import com.jumbo.stores.repository.StoreRepository;
import com.jumbo.stores.support.TestObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StoreServiceTest {
    @Test
    void ranksByDistanceThenIdWithoutMutatingRepository() {
        var far = store("far", 0, 2);
        var tieB = store("b", 0, 1);
        var closest = store("closest", 0, 0);
        var tieA = store("a", 0, 1);
        var snapshot = new ArrayList<>(List.of(far, tieB, closest, tieA));
        var service = new StoreService(() -> snapshot, new SearchProperties(5), new HaversineDistance());

        var result = service.findNearest(new Coordinates(0, 0), 5);

        assertEquals(List.of("closest", "a", "b", "far"),
                result.stores().stream().map(nearest -> nearest.store().id()).toList());
        assertEquals(0, result.stores().getFirst().distanceKm());
        assertEquals(111.1950802335329, result.stores().get(1).distanceKm(), 1e-9);
        assertEquals(222.3901604670658, result.stores().getLast().distanceKm(), 1e-9);
        assertEquals(List.of(far, tieB, closest, tieA), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> result.stores().clear());
    }

    @Test
    void usesInjectedCalculatorForDistancesAndRanking() {
        var origin = new Coordinates(0, 0);
        var near = store("near", 0, 1);
        var far = store("far", 0, 2);
        DistanceCalculator calculator = (from, to) -> {
            assertEquals(origin, from);
            return to.equals(far.coordinates()) ? 10 : 20;
        };
        var service = new StoreService(() -> List.of(near, far), new SearchProperties(5), calculator);

        var result = service.findNearest(origin, 5);

        assertEquals(List.of("far", "near"),
                result.stores().stream().map(nearest -> nearest.store().id()).toList());
        assertEquals(List.of(10.0, 20.0),
                result.stores().stream().map(nearest -> nearest.distanceKm()).toList());
    }

    @ParameterizedTest
    @CsvSource({"5,1,1", "5,3,3", "5,5,5", "5,10,5", "5,2147483647,5",
            "10,3,3", "10,10,10", "10,20,10"})
    void enforcesRequestedCountAndConfiguredCap(int cap, int requested, int expected) {
        var stores = IntStream.range(0, 12).mapToObj(index -> store("store-" + index, 52, 5)).toList();
        var result = service(stores, cap).findNearest(new Coordinates(52, 5), requested);
        assertEquals(expected, result.stores().size());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void returnsOnlyAvailableStoresAndHandlesEmptySnapshot() {
        var coordinates = TestObjects.coordinates().build();
        assertEquals(1, service(List.of(TestObjects.store().build()), 5)
                .findNearest(coordinates, 5).stores().size());
        var empty = service(List.of(), 5).findNearest(coordinates, 5);
        assertTrue(empty.stores().isEmpty());
        assertTrue(empty.warnings().isEmpty());
        var emptyOutside = service(List.of(), 5).findNearest(new Coordinates(0, 0), 5);
        assertEquals("OUTSIDE_SUPPORTED_AREA", emptyOutside.warnings().getFirst().code());
    }

    @Test
    void ranksAcrossAntimeridianUsingGeographicRatherThanCoordinateDifference() {
        var result = service(List.of(store("same-side", 0, 170), store("across", 0, -179)), 5)
                .findNearest(new Coordinates(0, 179), 5);
        assertEquals(List.of("across", "same-side"),
                result.stores().stream().map(nearest -> nearest.store().id()).toList());
    }

    @Test
    void sortsUnroundedDistancesBeforeApplyingIdTieBreak() {
        var result = service(List.of(store("a-farther", 0, 1.000000001), store("z-closer", 0, 1)), 5)
                .findNearest(new Coordinates(0, 0), 1);
        assertEquals("z-closer", result.stores().getFirst().store().id());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonpositiveDirectLimits(int limit) {
        assertThrows(IllegalArgumentException.class, () -> service(List.of(), 5)
                .findNearest(TestObjects.coordinates().build(), limit));
    }

    @Test
    void rejectsNullCoordinates() {
        assertThrows(NullPointerException.class, () -> service(List.of(), 5).findNearest(null, 5));
    }

    @ParameterizedTest
    @MethodSource("insideCoverage")
    void inclusiveCoverageEdgesHaveNoWarning(Coordinates coordinates) {
        assertTrue(service(List.of(), 5).findNearest(coordinates, 5).warnings().isEmpty());
    }

    static Stream<Coordinates> insideCoverage() {
        return Stream.of(new Coordinates(50.7, 3.2), new Coordinates(50.7, 7.3),
                new Coordinates(53.6, 3.2), new Coordinates(53.6, 7.3),
                new Coordinates(Math.nextUp(50.7), 5), new Coordinates(Math.nextDown(53.6), 5),
                new Coordinates(52, Math.nextUp(3.2)), new Coordinates(52, Math.nextDown(7.3)));
    }

    @ParameterizedTest
    @MethodSource("outsideCoverage")
    void outsideAnyCoverageEdgeWarnsButStillReturnsStores(Coordinates coordinates) {
        var result = service(List.of(TestObjects.store().build()), 5).findNearest(coordinates, 5);
        assertEquals(1, result.stores().size());
        assertEquals(1, result.warnings().size());
        assertEquals("OUTSIDE_SUPPORTED_AREA", result.warnings().getFirst().code());
        assertTrue(result.warnings().getFirst().message().contains("Netherlands"));
        assertTrue(result.warnings().getFirst().message().contains("far away"));
    }

    static Stream<Coordinates> outsideCoverage() {
        return Stream.of(new Coordinates(Math.nextDown(50.7), 5), new Coordinates(Math.nextUp(53.6), 5),
                new Coordinates(52, Math.nextDown(3.2)), new Coordinates(52, Math.nextUp(7.3)),
                new Coordinates(0, 0), new Coordinates(-90, -180), new Coordinates(90, 180));
    }

    private static StoreService service(List<Store> stores, int cap) {
        List<Store> snapshot = List.copyOf(stores);
        StoreRepository repository = () -> snapshot;
        return new StoreService(repository, new SearchProperties(cap), new HaversineDistance());
    }

    private static Store store(String id, double latitude, double longitude) {
        return TestObjects.store().id(id).coordinates(new Coordinates(latitude, longitude)).build();
    }
}
