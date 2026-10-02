package com.jumbo.stores.service;

import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.CoverageArea;
import com.jumbo.stores.domain.NearestStore;
import com.jumbo.stores.repository.StoreRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoreService {
    static final String OUTSIDE_COVERAGE_MESSAGE =
            "Search position is outside the supported coverage area; results may be far away";

    static final int MAX_RESULTS = 5;

    private static final Comparator<NearestStore> NEAREST_FIRST = Comparator.comparingDouble(NearestStore::distanceKm)
            .thenComparing(result -> result.store().id());

    private final StoreRepository repository;
    private final DistanceCalculator distanceCalculator;

    public List<NearestStore> findNearest(Coordinates coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (!CoverageArea.NETHERLANDS.contains(coordinates)) {
            log.warn(OUTSIDE_COVERAGE_MESSAGE);
        }
        return repository.findAll().stream()
                .map(store -> new NearestStore(store, distanceCalculator.between(coordinates, store.coordinates())))
                .sorted(NEAREST_FIRST)
                .limit(MAX_RESULTS)
                .toList();
    }
}
