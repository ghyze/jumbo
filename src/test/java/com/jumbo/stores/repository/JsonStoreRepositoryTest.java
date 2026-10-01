package com.jumbo.stores.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.stores.domain.Coordinates;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class JsonStoreRepositoryTest {
    @Test
    void mapsActualRootStructureAndEveryFieldIgnoringMetadata() {
        var repository = repository("""
                {
                  "attributes": {"irrelevant": true},
                  "stores": [
                    {
                      "unknown": {"nested": [1, 2]},
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "street2": "42",
                      "street3": "bis",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }
                  ]
                }
                """);

        var store = repository.findAll().getFirst();

        assertEquals("seed-a", store.id());
        assertEquals("Jumbo Distinct Name", store.addressName());
        assertEquals("Utrecht", store.city());
        assertEquals("3511 AB", store.postalCode());
        assertEquals("Voorstraat", store.street());
        assertEquals("42", store.street2());
        assertEquals("bis", store.street3());
        assertEquals(new Coordinates(52.0907, 5.1214), store.coordinates());
    }

    @Test
    void doesNotFilterClosedStoresOrCollectionPoints() {
        var stores = repository("""
                {
                  "stores": [
                    {
                      "todayOpen": "Gesloten",
                      "collectionPoint": false,
                      "uuid": "seed-a",
                      "addressName": "Jumbo Closed",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    },
                    {
                      "locationType": "PuP",
                      "collectionPoint": true,
                      "uuid": "seed-b",
                      "addressName": "Jumbo Pickup",
                      "city": "Utrecht",
                      "postalCode": "3511 CD",
                      "street": "Achterstraat",
                      "latitude": "52.0908",
                      "longitude": "5.1215"
                    }
                  ]
                }
                """).findAll();

        assertEquals(2, stores.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-90", "90"})
    void acceptsGlobalCoordinateBoundariesRatherThanRestrictingToNetherlands(String latitude) {
        var stores = repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo West",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "%1$s",
                      "longitude": "-180"
                    },
                    {
                      "uuid": "seed-b",
                      "addressName": "Jumbo East",
                      "city": "Utrecht",
                      "postalCode": "3511 CD",
                      "street": "Achterstraat",
                      "latitude": "%1$s",
                      "longitude": "180"
                    }
                  ]
                }
                """.formatted(latitude)).findAll();

        assertEquals(2, stores.size());
        assertEquals(Double.parseDouble(latitude), stores.getFirst().coordinates().latitude());
        assertEquals(-180, stores.getFirst().coordinates().longitude());
        assertEquals(180, stores.getLast().coordinates().longitude());
    }

    @Test
    void missingOptionalAddressPartsBecomeEmptyStrings() {
        var store = repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }
                  ]
                }
                """).findAll().getFirst();

        assertEquals("", store.street2());
        assertEquals("", store.street3());
    }

    @Test
    void nullOptionalAddressPartsBecomeEmptyStrings() {
        var store = repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "street2": null,
                      "street3": null,
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }
                  ]
                }
                """).findAll().getFirst();

        assertEquals("", store.street2());
        assertEquals("", store.street3());
    }

    @Test
    void readsResourceExactlyOnceClosesItAndReturnsSameImmutableSnapshot() {
        var resource = new CountingResource(validDocument());
        var repository = new JsonStoreRepository(resource);

        assertEquals(1, resource.reads);
        assertTrue(resource.closes >= 1);
        var snapshot = repository.findAll();
        assertSame(snapshot, repository.findAll());
        assertSame(snapshot, repository.findAll());
        assertEquals(1, resource.reads);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));
    }

    @ParameterizedTest
    @MethodSource("failingDocuments")
    void closesResourceWhenParsingOrValidationFails(String json) {
        var resource = new CountingResource(json);

        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(resource));

        assertEquals(1, resource.reads);
        assertTrue(resource.closes >= 1);
    }

    static Stream<String> failingDocuments() {
        return Stream.of("{", "{}", """
                {"stores": [null]}
                """, """
                {"stores": [{"uuid": "broken"}]}
                """);
    }

    @Test
    void invalidStoreRetainsResourceIndexIdAndOriginalCause() {
        var failure = assertThrows(IllegalStateException.class, () -> repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    },
                    {
                      "uuid": "seed-b",
                      "addressName": "Jumbo Broken",
                      "city": null,
                      "postalCode": "3511 CD",
                      "street": "Achterstraat",
                      "latitude": "52.0908",
                      "longitude": "5.1215"
                    }
                  ]
                }
                """));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[1] (uuid 'seed-b'): city must not be blank", failure.getMessage());
        assertEquals("city must not be blank", failure.getCause().getCause().getMessage());
    }

    @ParameterizedTest
    @MethodSource("blankRequiredFields")
    void domainRejectsBlankStoreFieldsWithRepositoryContext(String json, String id, String domainMessage) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(json));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[0] (uuid '" + id + "'): " + domainMessage, failure.getMessage());
        assertEquals(domainMessage, failure.getCause().getCause().getMessage());
    }

    static Stream<Arguments> blankRequiredFields() {
        return Stream.of(
                Arguments.of("""
                        {"stores": [{
                          "uuid": " ",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, " ", "id must not be blank"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "seed-a", "addressName must not be blank"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": " ",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "seed-a", "city must not be blank"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "seed-a", "postalCode must not be blank"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "seed-a", "street must not be blank"));
    }

    @ParameterizedTest
    @MethodSource("invalidShapes")
    void rejectsInvalidRootStoresOrEntryShape(String json, String expected) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(json));

        assertTrue(failure.getMessage().contains("fixture"));
        assertTrue(failure.getMessage().contains(expected), failure.getMessage());
        assertNotNull(failure.getCause());
    }

    static Stream<Arguments> invalidShapes() {
        return Stream.of(
                Arguments.of("", "JSON structure does not match the store data format"),
                Arguments.of("{", "Unexpected end-of-input"),
                Arguments.of("null", "stores must be a nonempty array"),
                Arguments.of("[]", "JSON structure does not match the store data format"),
                Arguments.of("42", "JSON structure does not match the store data format"),
                Arguments.of("{}", "stores must be a nonempty array"),
                Arguments.of("{\"stores\": null}", "stores must be a nonempty array"),
                Arguments.of("{\"stores\": {}}", "JSON structure does not match the store data format"),
                Arguments.of("{\"stores\": []}", "stores must be a nonempty array"),
                Arguments.of("{\"stores\": [null]}", "stores[0]: store must be an object"),
                Arguments.of("{\"stores\": [42]}", "JSON structure does not match the store data format"),
                Arguments.of("{\"stores\": [[]]}", "JSON structure does not match the store data format"));
    }

    @Test
    void rejectsTrailingJsonDocument() {
        var failure = assertThrows(IllegalStateException.class, () -> repository(validDocument() + "{}"));

        assertTrue(failure.getMessage().contains("JSON structure does not match the store data format"),
                failure.getMessage());
    }

    @Test
    void rejectsDuplicateIdsWithIndexAndId() {
        var failure = assertThrows(IllegalStateException.class, () -> repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo First",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    },
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Duplicate",
                      "city": "Utrecht",
                      "postalCode": "3511 CD",
                      "street": "Achterstraat",
                      "latitude": "52.0908",
                      "longitude": "5.1215"
                    }
                  ]
                }
                """));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[1] (uuid 'seed-a'): duplicate uuid 'seed-a'", failure.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "uuid, id must not be blank",
            "addressName, addressName must not be blank",
            "city, city must not be blank",
            "postalCode, postalCode must not be blank",
            "street, street must not be blank"
    })
    void rejectsMissingRequiredTextFields(String field, String message) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(requiredFieldMissing(field)));

        var context = field.equals("uuid") ? "stores[0]" : "stores[0] (uuid 'seed-a')";
        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + context + ": " + message, failure.getMessage());
    }

    @Test
    void coercesNumbersAndBooleansInStringFields() {
        var store = repository("""
                {
                  "stores": [
                    {
                      "uuid": 123,
                      "addressName": true,
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "street2": 42,
                      "street3": false,
                      "latitude": 52.0907,
                      "longitude": 5.1214
                    }
                  ]
                }
                """).findAll().getFirst();

        assertEquals("123", store.id());
        assertEquals("true", store.addressName());
        assertEquals("42", store.street2());
        assertEquals("false", store.street3());
        assertEquals(new Coordinates(52.0907, 5.1214), store.coordinates());
    }

    @ParameterizedTest
    @MethodSource("objectOrArrayStringFields")
    void rejectsObjectsAndArraysInStringFields(String json, String path) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(json));

        assertTrue(failure.getMessage().contains("JSON structure does not match the store data format"));
        assertTrue(failure.getMessage().contains(path), failure.getMessage());
    }

    static Stream<Arguments> objectOrArrayStringFields() {
        return Stream.of(
                Arguments.of("""
                        {"stores": [{
                          "uuid": {},
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "uuid"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": [],
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "city"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "street2": {},
                          "latitude": "52.0907",
                          "longitude": "5.1214"
                        }]}
                        """, "street2"));
    }

    @ParameterizedTest
    @MethodSource("missingCoordinates")
    void rejectsMissingCoordinates(String json, String expectedMessage) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(json));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[0] (uuid 'seed-a'): " + expectedMessage, failure.getMessage());
    }

    static Stream<Arguments> missingCoordinates() {
        return Stream.of(
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "longitude": "5.1214"
                        }]}
                        """, "latitude is required"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907"
                        }]}
                        """, "longitude is required"));
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void rejectsUnparseableNonfiniteAndOutOfRangeCoordinates(String field, String value, String expectedMessage) {
        var failure = assertThrows(IllegalStateException.class, () -> repository("""
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "%s",
                      "longitude": "%s"
                    }
                  ]
                }
                """.formatted(field.equals("latitude") ? value : "52.0907",
                field.equals("longitude") ? value : "5.1214")));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[0] (uuid 'seed-a'): " + expectedMessage, failure.getMessage());
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.of(
                Arguments.of("latitude", "text", "latitude must be a numeric string"),
                Arguments.of("latitude", "NaN", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "Infinity", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "-Infinity", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "1e309", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "90.01", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "-90.01", "latitude must be finite and between -90 and 90"),
                Arguments.of("longitude", "text", "longitude must be a numeric string"),
                Arguments.of("longitude", "NaN", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "Infinity", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "-Infinity", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "1e309", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "180.01", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "-180.01", "longitude must be finite and between -180 and 180"));
    }

    @Test
    void missingResourceFailsEagerlyWithResourceContext() {
        var failure = assertThrows(IllegalStateException.class,
                () -> new JsonStoreRepository(new ClassPathResource("missing-stores-test.json")));

        assertTrue(failure.getMessage().contains("missing-stores-test.json"));
        assertNotNull(failure.getCause());
    }

    @Test
    void unreadableResourceFailsEagerlyWithResourceContext() {
        var resource = new AbstractResource() {
            @Override
            public String getDescription() {
                return "unreadable seed";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("read denied");
            }
        };

        var failure = assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(resource));

        assertTrue(failure.getMessage().contains("unreadable seed"));
        assertTrue(failure.getMessage().contains("read denied"));
    }

    @Test
    void actualPackagedProductionResourceLoadsAndMapsKnownFirstStore() {
        var repository = new JsonStoreRepository(new ClassPathResource("stores.json"));
        var stores = repository.findAll();

        assertTrue(stores.stream().map(store -> store.id()).allMatch(id -> id != null && !id.isBlank()));
        assertEquals(stores.size(), stores.stream().map(store -> store.id()).distinct().count());
        var first = stores.getFirst();
        assertEquals("EOgKYx4XFiQAAAFJa_YYZ4At", first.id());
        assertEquals("Jumbo 's Gravendeel Gravendeel Centrum", first.addressName());
        assertEquals("'s Gravendeel", first.city());
        assertEquals("3295 BD", first.postalCode());
        assertEquals("Kerkstraat", first.street());
        assertEquals("37", first.street2());
        assertEquals("", first.street3());
        assertEquals(new Coordinates(51.778461, 4.615551), first.coordinates());
    }

    private static String validDocument() {
        return """
                {
                  "stores": [
                    {
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "street2": "42",
                      "street3": "bis",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }
                  ]
                }
                """;
    }

    private static String requiredFieldMissing(String field) {
        return switch (field) {
            case "uuid" -> """
                    {"stores": [{
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }]}
                    """;
            case "addressName" -> """
                    {"stores": [{
                      "uuid": "seed-a",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }]}
                    """;
            case "city" -> """
                    {"stores": [{
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "postalCode": "3511 AB",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }]}
                    """;
            case "postalCode" -> """
                    {"stores": [{
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "street": "Voorstraat",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }]}
                    """;
            case "street" -> """
                    {"stores": [{
                      "uuid": "seed-a",
                      "addressName": "Jumbo Distinct Name",
                      "city": "Utrecht",
                      "postalCode": "3511 AB",
                      "latitude": "52.0907",
                      "longitude": "5.1214"
                    }]}
                    """;
            default -> throw new IllegalArgumentException(field);
        };
    }

    private static JsonStoreRepository repository(String json) {
        return new JsonStoreRepository(new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8), "fixture"));
    }

    private static final class CountingResource extends AbstractResource {
        private final byte[] bytes;
        private int reads;
        private int closes;

        private CountingResource(String json) {
            bytes = json.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getDescription() {
            return "counting fixture";
        }

        @Override
        public InputStream getInputStream() {
            reads++;
            return new ByteArrayInputStream(bytes) {
                @Override
                public void close() throws IOException {
                    closes++;
                    super.close();
                }
            };
        }
    }
}
