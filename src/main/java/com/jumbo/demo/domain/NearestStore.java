package com.jumbo.demo.domain;

import java.util.Objects;
import lombok.Builder;

@Builder
public record NearestStore(Store store, double distanceKm) {
    public NearestStore {
        Objects.requireNonNull(store, "store");
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm must be finite and nonnegative");
        }
    }
}
