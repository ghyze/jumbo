package com.jumbo.stores.api;

import com.jumbo.stores.api.generated.StoresApi;
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
    public ResponseEntity<NearestStoresResponse> findNearestStores(Double latitude, Double longitude) {
        var coordinates = new Coordinates(latitude, longitude);
        var stores = service.findNearest(coordinates).stream().map(StoreController::toResponse).toList();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(new NearestStoresResponse(stores));
    }

    static StoreResponse toResponse(NearestStore nearest) {
        var store = nearest.store();
        return new StoreResponse()
                .id(store.id())
                .addressName(store.addressName())
                .city(store.city())
                .postalCode(store.postalCode())
                .street(store.street())
                .street2(store.street2())
                .street3(store.street3())
                .latitude(store.coordinates().latitude())
                .longitude(store.coordinates().longitude())
                .distanceKm(nearest.distanceKm());
    }
}
