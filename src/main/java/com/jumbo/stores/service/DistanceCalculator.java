package com.jumbo.stores.service;

import com.jumbo.stores.domain.Coordinates;

@FunctionalInterface
public interface DistanceCalculator {
    /**
     * Returns a finite, nonnegative distance in kilometres between non-null coordinates.
     *
     * @throws NullPointerException if either coordinate is null
     */
    double between(Coordinates from, Coordinates to);
}
