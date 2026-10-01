package com.jumbo.stores.repository;

import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public final class JsonStoreRepository implements StoreRepository {
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
            var mapper = JsonMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
            StoresFile storesFile = mapper.readValue(input, StoresFile.class);
            return parseStores(storesFile);
        } catch (MismatchedInputException exception) {
            throw new IllegalStateException("Failed to load stores from " + resource.getDescription()
                    + ": JSON structure does not match the store data format"
                    + pathReference(exception), exception);
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load stores from " + resource.getDescription()
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static String pathReference(MismatchedInputException exception) {
        String reference = exception.getPathReference();
        return reference == null || reference.isBlank() ? "" : " at " + reference;
    }

    private static List<Store> parseStores(StoresFile storesFile) {
        if (storesFile == null || storesFile.stores() == null || storesFile.stores().isEmpty()) {
            throw new IllegalArgumentException("stores must be a nonempty array");
        }
        var result = new ArrayList<Store>();
        var ids = new HashSet<String>();
        for (int index = 0; index < storesFile.stores().size(); index++) {
            result.add(parseStore(storesFile.stores().get(index), index, ids));
        }
        return List.copyOf(result);
    }

    private static Store parseStore(StoreJson entry, int index, Set<String> ids) {
        try {
            if (entry == null) {
                throw new IllegalArgumentException("store must be an object");
            }
            Store store = entry.toStore();
            if (!ids.add(store.id())) {
                throw new IllegalArgumentException("duplicate uuid '" + store.id() + "'");
            }
            return store;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("stores[" + index + "]"
                    + (entry == null || entry.uuid() == null ? "" : " (uuid '" + entry.uuid() + "')")
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static double coordinate(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a numeric string", exception);
        }
    }

    private record StoresFile(List<StoreJson> stores) {
    }

    private record StoreJson(String uuid, String addressName, String city, String postalCode,
                             String street, String street2, String street3,
                             String latitude, String longitude) {
        private Store toStore() {
            return new Store(uuid, addressName, city, postalCode, street, street2, street3,
                    new Coordinates(coordinate(latitude, "latitude"), coordinate(longitude, "longitude")));
        }
    }
}
