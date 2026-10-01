package com.jumbo.stores.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import com.jumbo.stores.support.TestObjects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatesTest {
    @ParameterizedTest
    @CsvSource({"-90,-180", "-90,180", "90,-180", "90,180", "0,0", "52.0907,5.1214"})
    void acceptsGlobalInclusiveBounds(double latitude, double longitude) {
        var coordinates = new Coordinates(latitude, longitude);
        assertThat(coordinates.latitude()).isEqualTo(latitude);
        assertThat(coordinates.longitude()).isEqualTo(longitude);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.000001, 90.000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidLatitude(double latitude) {
        var exception = catchThrowableOfType(
                () -> TestObjects.coordinates().latitude(latitude).build(), InvalidCoordinatesException.class);
        assertThat(exception.field()).isEqualTo("latitude");
        assertThat(exception.getMessage()).doesNotContain(Double.toString(latitude));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.000001, 180.000001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidLongitude(double longitude) {
        var exception = catchThrowableOfType(
                () -> TestObjects.coordinates().longitude(longitude).build(), InvalidCoordinatesException.class);
        assertThat(exception.field()).isEqualTo("longitude");
        assertThat(exception.getMessage()).doesNotContain(Double.toString(longitude));
    }
}
