package com.jumbo.stores.domain;

import lombok.Builder;

@Builder
public record Coordinates(double latitude, double longitude) {
    public Coordinates {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new InvalidCoordinatesException("latitude", 90);
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new InvalidCoordinatesException("longitude", 180);
        }
    }
}
