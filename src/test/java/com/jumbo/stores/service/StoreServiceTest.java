package com.jumbo.stores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import com.jumbo.stores.domain.WarningCode;
import com.jumbo.stores.repository.StoreRepository;
import com.jumbo.stores.support.TestObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoreServiceTest {
    @Test
    void ranksByDistanceThenIdWithoutMutatingRepository() {
        var far = store("far", 0, 2);
        var tieB = store("b", 0, 1);
        var closest = store("closest", 0, 0);
        var tieA = store("a", 0, 1);
        var snapshot = new ArrayList<>(List.of(far, tieB, closest, tieA));
        var service = new StoreService(() -> snapshot, new SearchProperties(5), new HaversineDistance());

        var result = service.findNearest(new Coordinates(0, 0));

        assertThat(result.stores().stream().map(nearest -> nearest.store().id()))
                .containsExactly("closest", "a", "b", "far");
        assertThat(result.stores().getFirst().distanceKm()).isZero();
        assertThat(result.stores().get(1).distanceKm()).isCloseTo(111.1950802335329, within(1e-9));
        assertThat(result.stores().getLast().distanceKm()).isCloseTo(222.3901604670658, within(1e-9));
        assertThat(snapshot).containsExactly(far, tieB, closest, tieA);
        assertThatThrownBy(() -> result.stores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesInjectedCalculatorForDistancesAndRanking() {
        var origin = new Coordinates(0, 0);
        var near = store("near", 0, 1);
        var far = store("far", 0, 2);
        DistanceCalculator calculator = (from, to) -> {
            assertThat(from).isEqualTo(origin);
            return to.equals(far.coordinates()) ? 10 : 20;
        };
        var service = new StoreService(() -> List.of(near, far), new SearchProperties(5), calculator);

        var result = service.findNearest(origin);

        assertThat(result.stores().stream().map(nearest -> nearest.store().id()))
                .containsExactly("far", "near");
        assertThat(result.stores().stream().map(nearest -> nearest.distanceKm()))
                .containsExactly(10.0, 20.0);
    }

    @ParameterizedTest
    @CsvSource({"5,5", "10,10"})
    void usesConfiguredResultCount(int maximum, int expected) {
        var stores = IntStream.range(0, 12).mapToObj(index -> store("store-" + index, 52, 5)).toList();
        var result = service(stores, maximum).findNearest(new Coordinates(52, 5));
        assertThat(result.stores()).hasSize(expected);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void returnsOnlyAvailableStoresAndHandlesEmptySnapshot() {
        var coordinates = TestObjects.coordinates().build();
        assertThat(service(List.of(TestObjects.store().build()), 5).findNearest(coordinates).stores()).hasSize(1);
        var empty = service(List.of(), 5).findNearest(coordinates);
        assertThat(empty.stores()).isEmpty();
        assertThat(empty.warnings()).isEmpty();
        var emptyOutside = service(List.of(), 5).findNearest(new Coordinates(0, 0));
        assertThat(emptyOutside.warnings().getFirst().code()).isEqualTo(WarningCode.OUTSIDE_SUPPORTED_AREA);
    }

    @Test
    void ranksAcrossAntimeridianUsingGeographicRatherThanCoordinateDifference() {
        var result = service(List.of(store("same-side", 0, 170), store("across", 0, -179)), 5)
                .findNearest(new Coordinates(0, 179));
        assertThat(result.stores().stream().map(nearest -> nearest.store().id()))
                .containsExactly("across", "same-side");
    }

    @Test
    void sortsUnroundedDistancesBeforeApplyingIdTieBreak() {
        var result = service(List.of(store("a-farther", 0, 1.000000001), store("z-closer", 0, 1)), 5)
                .findNearest(new Coordinates(0, 0));
        assertThat(result.stores().getFirst().store().id()).isEqualTo("z-closer");
    }

    @Test
    void rejectsNullCoordinates() {
        assertThatNullPointerException().isThrownBy(() -> service(List.of(), 5).findNearest(null));
    }

    @ParameterizedTest
    @CsvSource({
            "50.699999, 5", "53.600001, 5", "52, 3.199999", "52, 7.300001",
            "0, 0", "-90, -180", "90, 180"
    })
    void outsideAnyCoverageEdgeWarnsButStillReturnsStores(double latitude, double longitude) {
        var result = service(List.of(TestObjects.store().build()), 5).findNearest(new Coordinates(latitude, longitude));
        assertThat(result.stores()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().getFirst().code()).isEqualTo(WarningCode.OUTSIDE_SUPPORTED_AREA);
        assertThat(result.warnings().getFirst().message()).contains("Netherlands", "far away");
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
