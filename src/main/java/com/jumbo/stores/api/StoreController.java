package com.jumbo.stores.api;

import com.jumbo.stores.api.generated.StoresApi;
import com.jumbo.stores.api.generated.model.ApiWarning;
import com.jumbo.stores.api.generated.model.NearestStoresResponse;
import com.jumbo.stores.api.generated.model.StoreResponse;
import com.jumbo.stores.service.SearchProperties;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.NearestStore;
import com.jumbo.stores.service.StoreService;
import java.util.ArrayList;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreController implements StoresApi {
    private final StoreService service;
    private final LimitResolver limitResolver;

    public StoreController(StoreService service, SearchProperties properties) {
        this.service = service;
        this.limitResolver = new LimitResolver(properties);
    }

    @Override
    public ResponseEntity<NearestStoresResponse> findNearestStores(
            Double latitude, Double longitude, String limit) {
        var resolvedLimit = limitResolver.resolve(limit);
        var result = service.findNearest(new Coordinates(latitude, longitude), resolvedLimit.count());
        var stores = result.stores().stream().map(StoreController::toResponse).toList();
        var warnings = result.warnings().stream()
                .map(warning -> new ApiWarning(warning.code(), warning.message()))
                .toList();
        if (resolvedLimit.defaulted()) {
            warnings = new ArrayList<>(warnings);
            warnings.addFirst(new ApiWarning("INVALID_LIMIT_DEFAULTED",
                    "limit must be a positive decimal integer; the configured count of "
                            + resolvedLimit.count() + " was used."));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(new NearestStoresResponse(stores, warnings));
    }

    static StoreResponse toResponse(NearestStore nearest) {
        var store = nearest.store();
        return new StoreResponse(store.id(), store.addressName(), store.city(), store.postalCode(),
                store.street(), store.street2(), store.street3(), store.coordinates().latitude(),
                store.coordinates().longitude(), nearest.distanceKm());
    }
}
