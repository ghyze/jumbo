package com.jumbo.demo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.jumbo.demo.support.TestObjects;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DomainModelsTest {
    @Test
    void normalizesOptionalAddressParts() {
        var store = TestObjects.store().street2(null).street3(null).build();
        assertEquals("", store.street2());
        assertEquals("", store.street3());
    }

    @Test
    void rejectsInvalidStoreFields() {
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().id(" ").build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().addressName(null).build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().city("").build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().postalCode("\t").build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().street(null).build());
        assertThrows(NullPointerException.class, () -> TestObjects.store().coordinates(null).build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\r\n", "\u2003"})
    void domainProtectsEveryRequiredStoreFieldWithoutRepository(String value) {
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().id(value).build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().addressName(value).build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().city(value).build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().postalCode(value).build());
        assertThrows(IllegalArgumentException.class, () -> TestObjects.store().street(value).build());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidDistance(double distance) {
        assertThrows(IllegalArgumentException.class,
                () -> TestObjects.nearestStore().distanceKm(distance).build());
    }

    @Test
    void searchResultDefensivelyCopiesBothLists() {
        var stores = new ArrayList<>(List.of(TestObjects.nearestStore().build()));
        var warnings = new ArrayList<>(List.of(TestObjects.searchWarning().build()));
        var result = new SearchResult(stores, warnings);
        stores.clear();
        warnings.clear();
        assertEquals(1, result.stores().size());
        assertEquals(1, result.warnings().size());
        assertThrows(UnsupportedOperationException.class, () -> result.stores().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().clear());
    }

    @Test
    void validatesWarningsAndNearestStore() {
        assertThrows(IllegalArgumentException.class, () -> new SearchWarning("", "message"));
        assertThrows(IllegalArgumentException.class, () -> new SearchWarning("CODE", null));
        assertThrows(NullPointerException.class, () -> new NearestStore(null, 0));
        assertEquals(0, TestObjects.nearestStore().distanceKm(0).build().distanceKm());
    }
}
