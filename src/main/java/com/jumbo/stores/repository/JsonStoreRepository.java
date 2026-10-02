package com.jumbo.stores.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
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
            List<Store> stores = file == null || file.stores() == null ? List.of() : validStores(file.stores());
            if (stores.isEmpty()) {
                throw new IllegalArgumentException("no valid stores found");
            }
            return stores;
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load stores from " + resource.getDescription()
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static List<Store> validStores(List<JsonNode> entries) {
        var stores = new ArrayList<Store>();
        var ids = new HashSet<String>();
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            try {
                Store store = toStore(entry);
                if (!ids.add(store.id())) {
                    throw new IllegalArgumentException("duplicate uuid '" + store.id() + "'");
                }
                stores.add(store);
            } catch (JacksonException | IllegalArgumentException exception) {
                log.warn("Skipping invalid store at stores[{}]: {}", index, reason(exception));
            }
        }
        return List.copyOf(stores);
    }

    private static Store toStore(JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            throw new IllegalArgumentException("store must be an object");
        }
        return MAPPER.treeToValue(entry, StoreJson.class).toStore();
    }

    private static String reason(RuntimeException exception) {
        return exception instanceof JacksonException jackson
                ? jackson.getOriginalMessage() + " at " + jackson.getPathReference()
                : exception.getMessage();
    }

    private record StoresFile(List<JsonNode> stores) {
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
