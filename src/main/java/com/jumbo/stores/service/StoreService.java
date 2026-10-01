package com.jumbo.stores.service;

import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.CoverageArea;
import com.jumbo.stores.domain.NearestStore;
import com.jumbo.stores.domain.SearchResult;
import com.jumbo.stores.domain.SearchWarning;
import com.jumbo.stores.domain.WarningCode;
import com.jumbo.stores.repository.StoreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoreService {
    private static final Comparator<NearestStore> NEAREST_FIRST = Comparator.comparingDouble(NearestStore::distanceKm)
            .thenComparing(result -> result.store().id());
    private static final SearchWarning OUTSIDE_COVERAGE = new SearchWarning(WarningCode.OUTSIDE_SUPPORTED_AREA,
            "The dataset covers the Netherlands; this location is outside the approximate coverage area "
                    + "and results may be far away.");

    private final StoreRepository repository;
    private final SearchProperties properties;
    private final DistanceCalculator distanceCalculator;

    public SearchResult findNearest(Coordinates coordinates) {
        return findNearest(coordinates, properties.maxResults());
    }

    public SearchResult findNearest(Coordinates coordinates, int limit) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var nearest = repository.findAll().stream()
                .map(store -> new NearestStore(store, distanceCalculator.between(coordinates, store.coordinates())))
                .sorted(NEAREST_FIRST)
                .limit(Math.min(limit, properties.maxResults()))
                .toList();
        var warnings = CoverageArea.NETHERLANDS.contains(coordinates) ? List.<SearchWarning>of() : List.of(OUTSIDE_COVERAGE);
        return new SearchResult(nearest, warnings);
    }
}