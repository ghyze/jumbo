package com.jumbo.stores.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoverageAreaTest {
    @ParameterizedTest
    @CsvSource({
            "50.7, 3.2", "50.7, 7.3", "53.6, 3.2", "53.6, 7.3",
            "50.700001, 5", "53.599999, 5", "52, 3.200001", "52, 7.299999"
    })
    void containsInclusiveCoverageEdges(double latitude, double longitude) {
        assertThat(CoverageArea.NETHERLANDS.contains(new Coordinates(latitude, longitude))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "50.699999, 5", "53.600001, 5", "52, 3.199999", "52, 7.300001",
            "0, 0", "-90, -180", "90, 180"
    })
    void excludesCoordinatesOutsideAnyCoverageEdge(double latitude, double longitude) {
        assertThat(CoverageArea.NETHERLANDS.contains(new Coordinates(latitude, longitude))).isFalse();
    }
}
