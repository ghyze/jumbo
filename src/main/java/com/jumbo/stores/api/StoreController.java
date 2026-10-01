package com.jumbo.stores.api;

import com.jumbo.stores.api.generated.StoresApi;
import com.jumbo.stores.api.generated.model.ApiWarning;
import com.jumbo.stores.api.generated.model.NearestStoresResponse;
import com.jumbo.stores.api.generated.model.StoreResponse;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.NearestStore;
import com.jumbo.stores.service.StoreService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreController implements StoresApi {
    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<NearestStoresResponse> findNearestStores(
            Double latitude, Double longitude, Integer limit) {
        var coordinates = new Coordinates(latitude, longitude);
        var result = limit == null ? service.findNearest(coordinates) : service.findNearest(coordinates, limit);
        var stores = result.stores().stream().map(StoreController::toResponse).toList();
        var warnings = result.warnings().stream()
                .map(warning -> new ApiWarning(warning.code(), warning.message()))
                .toList();
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
