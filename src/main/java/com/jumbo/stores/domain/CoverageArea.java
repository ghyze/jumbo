package com.jumbo.stores.domain;

public record CoverageArea(double minimumLatitude, double maximumLatitude,
                           double minimumLongitude, double maximumLongitude) {
    public static final CoverageArea NETHERLANDS = new CoverageArea(50.7, 53.6, 3.2, 7.3);

    public boolean contains(Coordinates coordinates) {
        return coordinates.latitude() >= minimumLatitude && coordinates.latitude() <= maximumLatitude
                && coordinates.longitude() >= minimumLongitude && coordinates.longitude() <= maximumLongitude;
    }
}
