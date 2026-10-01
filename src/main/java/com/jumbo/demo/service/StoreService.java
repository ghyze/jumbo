package com.jumbo.demo.service;

import com.jumbo.demo.config.SearchProperties;
import com.jumbo.demo.domain.Coordinates;
import com.jumbo.demo.domain.NearestStore;
import com.jumbo.demo.domain.SearchResult;
import com.jumbo.demo.domain.SearchWarning;
import com.jumbo.demo.repository.StoreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final StoreRepository repository;
    private final SearchProperties properties;
    private final DistanceCalculator distanceCalculator;

    public SearchResult findNearest(Coordinates coordinates, int limit) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var nearest = repository.findAll().stream()
                .map(store -> new NearestStore(store, distanceCalculator.between(coordinates, store.coordinates())))
                .sorted(Comparator.comparingDouble(NearestStore::distanceKm)
                        .thenComparing(result -> result.store().id()))
                .limit(Math.min(limit, properties.maxResults()))
                .toList();
        List<SearchWarning> warnings = isSupported(coordinates) ? List.of() : List.of(new SearchWarning(
                "OUTSIDE_SUPPORTED_AREA",
                "The dataset covers the Netherlands; this location is outside the approximate coverage area "
                        + "and results may be far away."));
        return new SearchResult(nearest, warnings);
    }

    private static boolean isSupported(Coordinates coordinates) {
        return coordinates.latitude() >= 50.7 && coordinates.latitude() <= 53.6
                && coordinates.longitude() >= 3.2 && coordinates.longitude() <= 7.3;
    }
}
