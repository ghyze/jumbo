package com.jumbo.demo.repository;

import com.jumbo.demo.domain.Coordinates;
import com.jumbo.demo.domain.Store;
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
import tools.jackson.databind.JsonNode;
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
            JsonNode entries = storeEntries(mapper.readTree(input));
            return parseStores(entries);
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to load stores from " + resource.getDescription()
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static JsonNode storeEntries(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("root must be an object containing a stores array");
        }
        JsonNode entries = root.get("stores");
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            throw new IllegalArgumentException("stores must be a nonempty array");
        }
        return entries;
    }

    private static List<Store> parseStores(JsonNode entries) {
        var result = new ArrayList<Store>();
        var ids = new HashSet<String>();
        for (int index = 0; index < entries.size(); index++) {
            result.add(parseStore(entries.get(index), index, ids));
        }
        return List.copyOf(result);
    }

    private static Store parseStore(JsonNode entry, int index, Set<String> ids) {
        String id = null;
        try {
            if (!entry.isObject()) {
                throw new IllegalArgumentException("store must be an object");
            }
            id = requiredString(entry, "uuid");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate uuid '" + id + "'");
            }
            return new Store(id, requiredString(entry, "addressName"),
                    requiredString(entry, "city"), requiredString(entry, "postalCode"),
                    requiredString(entry, "street"), optionalText(entry, "street2"),
                    optionalText(entry, "street3"),
                    new Coordinates(coordinate(entry, "latitude"), coordinate(entry, "longitude")));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("stores[" + index + "]"
                    + (id == null ? "" : " (uuid '" + id + "')")
                    + ": " + exception.getMessage(), exception);
        }
    }

    private static String requiredString(JsonNode entry, String field) {
        JsonNode value = entry.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asString();
    }

    private static String optionalText(JsonNode entry, String field) {
        JsonNode value = entry.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be a string when supplied");
        }
        return value.asString();
    }

    private static double coordinate(JsonNode entry, String field) {
        String value = requiredString(entry, field);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a numeric string", exception);
        }
    }
}
