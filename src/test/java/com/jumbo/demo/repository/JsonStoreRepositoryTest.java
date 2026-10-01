package com.jumbo.demo.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jumbo.demo.domain.Coordinates;
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
    private static final String STORE = """
            {"uuid":"seed-a","addressName":"Jumbo Distinct Name","city":"Utrecht",
             "postalCode":"3511 AB","street":"Voorstraat","street2":"42","street3":"bis",
             "latitude":"52.0907","longitude":"5.1214"}
            """;

    @Test
    void mapsActualRootStructureAndEveryFieldIgnoringMetadata() {
        var repository = repository("""
                {"attributes":{"irrelevant":true},"stores":[%s]}
                """.formatted(STORE.replace("\"uuid\":", "\"unknown\":{\"nested\":[1,2]},\"uuid\":")));
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
        String closed = STORE.replace("\"uuid\":", "\"todayOpen\":\"Gesloten\",\"collectionPoint\":false,\"uuid\":");
        String collectionPoint = STORE.replace("seed-a", "seed-b")
                .replace("\"uuid\":", "\"locationType\":\"PuP\",\"collectionPoint\":true,\"uuid\":");
        assertEquals(2, repository(document(closed + "," + collectionPoint)).findAll().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-90", "90"})
    void acceptsGlobalCoordinateBoundariesRatherThanRestrictingToNetherlands(String latitude) {
        var stores = repository(document(STORE.replace("52.0907", latitude).replace("5.1214", "-180")
                + "," + STORE.replace("seed-a", "seed-b").replace("52.0907", latitude)
                .replace("5.1214", "180"))).findAll();
        assertEquals(2, stores.size());
        assertEquals(Double.parseDouble(latitude), stores.getFirst().coordinates().latitude());
        assertEquals(-180, stores.getFirst().coordinates().longitude());
        assertEquals(180, stores.getLast().coordinates().longitude());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"street2\":\"42\",", "\"street3\":\"bis\","})
    void missingOptionalAddressPartBecomesEmptyString(String part) {
        var store = repository(document(STORE.replace(part, ""))).findAll().getFirst();
        assertEquals("", part.contains("street2") ? store.street2() : store.street3());
    }

    @Test
    void nullOptionalAddressPartsBecomeEmptyStrings() {
        var store = repository(document(STORE.replace("\"42\"", "null").replace("\"bis\"", "null")))
                .findAll().getFirst();
        assertEquals("", store.street2());
        assertEquals("", store.street3());
    }

    @Test
    void readsResourceExactlyOnceClosesItAndReturnsSameImmutableSnapshot() {
        var resource = new CountingResource(document(STORE));
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
    @ValueSource(strings = {"{", "{}", "{\"stores\":[null]}", "{\"stores\":[{\"uuid\":\"broken\"}]}"})
    void closesResourceWhenParsingOrValidationFails(String json) {
        var resource = new CountingResource(json);

        assertThrows(IllegalStateException.class, () -> new JsonStoreRepository(resource));

        assertEquals(1, resource.reads);
        assertTrue(resource.closes >= 1);
    }

    @Test
    void invalidStoreRetainsResourceIndexIdAndOriginalCause() {
        String invalid = STORE.replace("seed-a", "seed-b").replace("\"city\":\"Utrecht\"", "\"city\":null");

        var failure = assertThrows(IllegalStateException.class,
                () -> repository(document(STORE + "," + invalid)));

        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[1] (uuid 'seed-b'): city must be a string", failure.getMessage());
        assertEquals("city must be a string", failure.getCause().getCause().getMessage());
    }

    @ParameterizedTest
    @CsvSource({"uuid,seed-a,id", "addressName,Jumbo Distinct Name,addressName",
            "city,Utrecht,city", "postalCode,3511 AB,postalCode", "street,Voorstraat,street"})
    void domainRejectsBlankStoreFieldsWithRepositoryContext(String jsonField, String original, String domainField) {
        String invalid = STORE.replace("\"" + jsonField + "\":\"" + original + "\"",
                "\"" + jsonField + "\":\" \"");

        var failure = assertThrows(IllegalStateException.class, () -> repository(document(invalid)));

        String id = jsonField.equals("uuid") ? " " : "seed-a";
        String domainMessage = domainField + " must not be blank";
        assertEquals("Failed to load stores from Byte array resource [fixture]: "
                + "stores[0] (uuid '" + id + "'): " + domainMessage, failure.getMessage());
        assertEquals(domainMessage, failure.getCause().getCause().getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "null", "[]", "42", "{}", "{\"stores\":null}",
            "{\"stores\":{}}", "{\"stores\":[]}", "{\"stores\":[null]}", "{\"stores\":[42]}",
            "{\"stores\":[[]]}"})
    void rejectsInvalidRootOrStoreShape(String json) {
        var failure = assertThrows(IllegalStateException.class, () -> repository(json));
        assertTrue(failure.getMessage().contains("fixture"));
        assertNotNull(failure.getCause());
    }

    @Test
    void rejectsTrailingJsonDocument() {
        assertThrows(IllegalStateException.class, () -> repository(document(STORE) + "{}"));
    }

    @Test
    void rejectsDuplicateIdsWithIndexAndId() {
        var failure = assertThrows(IllegalStateException.class,
                () -> repository(document(STORE + "," + STORE)));
        assertTrue(failure.getMessage().contains("duplicate uuid 'seed-a'"));
        assertTrue(failure.getMessage().contains("stores[1]"));
    }

    @ParameterizedTest
    @MethodSource("invalidRequiredFields")
    void rejectsMissingNullBlankOrWrongTypeRequiredFields(String field, String original, String replacement) {
        String invalid = replacement == null
                ? STORE.replace("\"" + field + "\":" + original + ",", "")
                : STORE.replace("\"" + field + "\":" + original, "\"" + field + "\":" + replacement);
        var failure = assertThrows(IllegalStateException.class, () -> repository(document(invalid)));
        assertTrue(failure.getMessage().contains(field), failure.getMessage());
        assertTrue(failure.getMessage().contains("stores[0]"), failure.getMessage());
    }

    static Stream<Arguments> invalidRequiredFields() {
        return Stream.of(
                new String[]{"uuid", "\"seed-a\""},
                new String[]{"addressName", "\"Jumbo Distinct Name\""},
                new String[]{"city", "\"Utrecht\""},
                new String[]{"postalCode", "\"3511 AB\""},
                new String[]{"street", "\"Voorstraat\""},
                new String[]{"latitude", "\"52.0907\""})
                .flatMap(field -> Stream.of(null, "null", "\"\"", "\"  \"", "123", "true", "{}", "[]")
                        .map(replacement -> Arguments.of(field[0], field[1], replacement)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\" \"", "123", "true", "{}", "[]"})
    void rejectsInvalidLongitudeField(String replacement) {
        var failure = assertThrows(IllegalStateException.class,
                () -> repository(document(STORE.replace("\"5.1214\"", replacement))));
        assertTrue(failure.getMessage().contains("longitude"));
    }

    @Test
    void rejectsMissingLongitudeField() {
        var failure = assertThrows(IllegalStateException.class,
                () -> repository(document(STORE.replace(",\"longitude\":\"5.1214\"", ""))));
        assertTrue(failure.getMessage().contains("longitude"));
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void rejectsUnparseableNonfiniteAndOutOfRangeCoordinates(String field, String original, String invalid) {
        var failure = assertThrows(IllegalStateException.class,
                () -> repository(document(STORE.replace(original, invalid))));
        assertTrue(failure.getMessage().contains(field), failure.getMessage());
        assertTrue(failure.getMessage().contains("seed-a"));
        assertTrue(failure.getMessage().contains("stores[0]"));
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.concat(
                Stream.of("text", "NaN", "Infinity", "-Infinity", "1e309", "90.01", "-90.01")
                        .map(value -> Arguments.of("latitude", "52.0907", value)),
                Stream.of("text", "NaN", "Infinity", "-Infinity", "1e309", "180.01", "-180.01")
                        .map(value -> Arguments.of("longitude", "5.1214", value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "true", "{}", "[]"})
    void rejectsNonStringOptionalAddressParts(String value) {
        assertThrows(IllegalStateException.class,
                () -> repository(document(STORE.replace("\"42\"", value))));
        assertThrows(IllegalStateException.class,
                () -> repository(document(STORE.replace("\"bis\"", value))));
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
    void actualPackagedProductionResourceLoadsAll587Stores() {
        var repository = new JsonStoreRepository(new ClassPathResource("stores.json"));
        var stores = repository.findAll();
        assertEquals(587, stores.size());
        assertEquals(587, stores.stream().map(store -> store.id()).distinct().count());
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

    private static String document(String stores) {
        return "{\"stores\":[" + stores + "]}";
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
