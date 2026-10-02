package com.jumbo.stores.support;

import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.NearestStore;
import com.jumbo.stores.domain.Store;

public final class TestObjects {
    private TestObjects() {
    }

    public static Coordinates.CoordinatesBuilder coordinates() {
        return Coordinates.builder().latitude(52.0907).longitude(5.1214);
    }

    public static Store.StoreBuilder store() {
        return Store.builder().id("store-a").addressName("Jumbo Utrecht Centrum").city("Utrecht")
                .postalCode("3511 AA").street("Voorstraat").street2("12").street3("A")
                .coordinates(coordinates().build());
    }

    public static NearestStore.NearestStoreBuilder nearestStore() {
        return NearestStore.builder().store(store().build()).distanceKm(1.25);
    }

}
