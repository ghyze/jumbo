package com.jumbo.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.demo.domain.Coordinates;
import com.jumbo.demo.support.TestObjects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HaversineDistanceTest {
    private final DistanceCalculator calculator = new HaversineDistance();

    @Test
    void rejectsNullCoordinates() {
        var point = TestObjects.coordinates().build();
        assertThrows(NullPointerException.class, () -> calculator.between(null, point));
        assertThrows(NullPointerException.class, () -> calculator.between(point, null));
    }

    @Test
    void samePointHasZeroDistance() {
        var point = TestObjects.coordinates().build();
        assertEquals(0, calculator.between(point, point));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,1,111.1950802335329,0.000000001",
            "0,0,90,0,10007.557221017962,0.000000001",
            "0,0,0,180,20015.114442035924,0.000000001",
            "90,0,-90,170,20015.114442035924,0.000000001",
            "0,179,0,-179,222.3901604670658,0.000000001",
            "90,-120,90,70,0,0.000000001",
            "51.5074,-0.1278,48.8566,2.3522,343.5565,0.001",
            "52.0907,5.1214,52.3676,4.9041,34.16210554893698,0.000001"
    })
    void matchesIndependentKnownDistances(double latitude1, double longitude1, double latitude2,
                                         double longitude2, double expected, double tolerance) {
        var from = new Coordinates(latitude1, longitude1);
        var to = new Coordinates(latitude2, longitude2);
        assertEquals(expected, calculator.between(from, to), tolerance);
        assertEquals(expected, calculator.between(to, from), tolerance);
    }

    @ParameterizedTest
    @CsvSource({"27.3,13.4,-27.3,-166.6", "89.999999,0,-89.999999,179.999999",
            "0,0,0.000000001,179.999999999", "53.4,6.1,-53.4,-173.9"})
    void antipodalRoundingRemainsFinite(double latitude1, double longitude1,
                                       double latitude2, double longitude2) {
        double result = calculator.between(new Coordinates(latitude1, longitude1),
                new Coordinates(latitude2, longitude2));
        assertTrue(Double.isFinite(result));
        assertEquals(20015.114442035924, result, 0.001);
    }
}
