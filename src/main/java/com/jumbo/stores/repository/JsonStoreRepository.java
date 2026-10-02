package com.jumbo.stores.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public final class JsonStoreRepository implements StoreRepository {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final List<Store> stores;

    public JsonStoreRepository(Resource resource) {
        Objects.requireNonNull(resource, "resource");
        this.stores = load(resource);
        log.info("Loaded {} stores from {}", stores.size(), resource.getDescription());
    }

    @Override
    public List<Store> findAll() {
        return stores;
    }

    private static List<Store> load(Resource resource) {
        try (var input = resource.getInputStream()) {
            StoresFile file = MAPPER.readValue(input, StoresFile.class);
            if (file == null || file.stores() == null || file.stores().isEmpty()) {
                throw new IllegalArgumentException("stores must be a nonempty array");
            }
            List<Store> stores = file.stores().stream().map(StoreJson::toStore).toList();
            requireUniqueIds(stores);
            return stores;
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load stores from " + resource.getDescription()
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static void requireUniqueIds(List<Store> stores) {
        var ids = new HashSet<String>();
        for (Store store : stores) {
            if (!ids.add(store.id())) {
                throw new IllegalArgumentException("duplicate uuid '" + store.id() + "'");
            }
        }
    }

    private record StoresFile(@JsonSetter(contentNulls = Nulls.FAIL) List<StoreJson> stores) {
    }

    private record StoreJson(String uuid, String addressName, String city, String postalCode,
                             String street, String street2, String street3,
                             @JsonProperty(required = true) double latitude,
                             @JsonProperty(required = true) double longitude) {
        private Store toStore() {
            try {
                return new Store(uuid, addressName, city, postalCode, street, street2, street3,
                        new Coordinates(latitude, longitude));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("store '" + uuid + "': " + exception.getMessage(), exception);
            }
        }
    }
}
