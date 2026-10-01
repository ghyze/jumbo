package com.jumbo.stores.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.jumbo.stores.support.TestObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatesTest {
    @ParameterizedTest
    @CsvSource({"-90,-180", "-90,180", "90,-180", "90,180", "0,0", "52.0907,5.1214"})
    void acceptsGlobalInclusiveBounds(double latitude, double longitude) {
        var coordinates = new Coordinates(latitude, longitude);
        assertEquals(latitude, coordinates.latitude());
        assertEquals(longitude, coordinates.longitude());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.000001, 90.000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidLatitude(double latitude) {
        assertThrows(IllegalArgumentException.class,
                () -> TestObjects.coordinates().latitude(latitude).build());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.000001, 180.000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidLongitude(double longitude) {
        assertThrows(IllegalArgumentException.class,
                () -> TestObjects.coordinates().longitude(longitude).build());
    }

    @Test
    void factoryBuildersAreFreshAndPreserveValidation() {
        TestObjects.coordinates().latitude(0).build();
        assertEquals(52.0907, TestObjects.coordinates().build().latitude());
        TestObjects.store().id("changed").build();
        assertEquals("store-a", TestObjects.store().build().id());
    }
}
