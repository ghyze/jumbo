package com.jumbo.stores.service;

import com.jumbo.stores.domain.Coordinates;
import java.util.Objects;

public final class HaversineDistance implements DistanceCalculator {
    /** IUGG mean Earth radius in kilometres; distances are great-circle, not road distances. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public double between(Coordinates from, Coordinates to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        double latitudeDelta = Math.toRadians(to.latitude() - from.latitude());
        double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
        double latitudeSine = Math.sin(latitudeDelta / 2);
        double longitudeSine = Math.sin(longitudeDelta / 2);
        double haversine = latitudeSine * latitudeSine
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * longitudeSine * longitudeSine;
        haversine = Math.clamp(haversine, 0, 1);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine));
    }
}
