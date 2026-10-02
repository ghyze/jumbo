package com.jumbo.stores.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import com.jumbo.stores.domain.Coordinates;
import com.jumbo.stores.domain.Store;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(OutputCaptureExtension.class)
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

        assertThat(store.id()).isEqualTo("seed-a");
        assertThat(store.addressName()).isEqualTo("Jumbo Distinct Name");
        assertThat(store.city()).isEqualTo("Utrecht");
        assertThat(store.postalCode()).isEqualTo("3511 AB");
        assertThat(store.street()).isEqualTo("Voorstraat");
        assertThat(store.street2()).isEqualTo("42");
        assertThat(store.street3()).isEqualTo("bis");
        assertThat(store.coordinates()).isEqualTo(new Coordinates(52.0907, 5.1214));
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

        assertThat(stores).hasSize(2);
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

        assertThat(stores).hasSize(2);
        assertThat(stores.getFirst().coordinates().latitude()).isEqualTo(Double.parseDouble(latitude));
        assertThat(stores.getFirst().coordinates().longitude()).isEqualTo(-180);
        assertThat(stores.getLast().coordinates().longitude()).isEqualTo(180);
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

        assertThat(store.street2()).isEmpty();
        assertThat(store.street3()).isEmpty();
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

        assertThat(store.street2()).isEmpty();
        assertThat(store.street3()).isEmpty();
    }

    @Test
    void readsResourceExactlyOnceClosesItAndReturnsSameImmutableSnapshot() {
        var resource = new CountingResource(validDocument());
        var repository = new JsonStoreRepository(resource);

        assertThat(resource.reads).isEqualTo(1);
        assertThat(resource.closes).isGreaterThanOrEqualTo(1);
        var snapshot = repository.findAll();
        assertThat(repository.findAll()).isSameAs(snapshot);
        assertThat(repository.findAll()).isSameAs(snapshot);
        assertThat(resource.reads).isEqualTo(1);
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.add(snapshot.getFirst())).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("failingDocuments")
    void closesResourceWhenParsingOrValidationFails(String json) {
        var resource = new CountingResource(json);

        assertThatThrownBy(() -> new JsonStoreRepository(resource)).isInstanceOf(IllegalStateException.class);

        assertThat(resource.reads).isEqualTo(1);
        assertThat(resource.closes).isGreaterThanOrEqualTo(1);
    }

    static Stream<String> failingDocuments() {
        return Stream.of("{", "{}", """
                {"stores": [null]}
                """, """
                {"stores": [{"uuid": "broken"}]}
                """);
    }

    @Test
    void skipsInvalidStoreWithWarningAndLoadsTheRest(CapturedOutput output) {
        var repository = repository("""
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
                """);

        assertThat(repository.findAll()).extracting(Store::id).containsExactly("seed-a");
        assertThat(output).contains("WARN")
                .contains("Skipping invalid store at stores[1]: store 'seed-b': city must not be blank");
    }

    @Test
    void failsWhenNoValidStoresRemain(CapturedOutput output) {
        var failure = catchThrowable(() -> repository("""
                {"stores": [{"uuid": "broken"}, 42]}
                """));

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load stores from Byte array resource [fixture]: no valid stores found");
        assertThat(output).contains("stores[0]").contains("stores[1]");
    }

    @ParameterizedTest
    @MethodSource("blankRequiredFields")
    void skipsStoresWithBlankRequiredFields(String json, String id, String domainMessage, CapturedOutput output) {
        assertOnlyStoreSkipped(json, "store '" + id + "': " + domainMessage, output);
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
    void rejectsInvalidDocumentShape(String json, String expected) {
        var failure = catchThrowable(() -> repository(json));

        assertThat(failure).hasMessageContaining("fixture");
        assertThat(failure).hasMessageContaining(expected);
        assertThat(failure).cause().isNotNull();
    }

    static Stream<Arguments> invalidShapes() {
        return Stream.of(
                Arguments.of("", "No content to map"),
                Arguments.of("{", "Unexpected end-of-input"),
                Arguments.of("null", "no valid stores found"),
                Arguments.of("[]", "from Array value"),
                Arguments.of("42", "from Number value (42)"),
                Arguments.of("{}", "no valid stores found"),
                Arguments.of("{\"stores\": null}", "no valid stores found"),
                Arguments.of("{\"stores\": {}}", "[\"stores\"]"),
                Arguments.of("{\"stores\": []}", "no valid stores found"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "[]", "\"text\""})
    void skipsEntriesThatAreNotObjects(String entry, CapturedOutput output) {
        assertOnlyStoreSkipped("{\"stores\": [" + entry + "]}", "store must be an object", output);
    }

    @Test
    void rejectsTrailingJsonDocument() {
        var failure = catchThrowable(() -> repository(validDocument() + "{}"));

        assertThat(failure).hasMessageContaining("Trailing token");
    }

    @Test
    void keepsFirstStoreAndSkipsDuplicateIds(CapturedOutput output) {
        var repository = repository("""
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
                """);

        assertThat(repository.findAll()).extracting(Store::addressName).containsExactly("Jumbo First");
        assertThat(output).contains("Skipping invalid store at stores[1]: duplicate uuid 'seed-a'");
    }

    @ParameterizedTest
    @CsvSource({
            "uuid, id must not be blank",
            "addressName, addressName must not be blank",
            "city, city must not be blank",
            "postalCode, postalCode must not be blank",
            "street, street must not be blank"
    })
    void skipsStoresMissingRequiredTextFields(String field, String message, CapturedOutput output) {
        var id = field.equals("uuid") ? "null" : "seed-a";
        assertOnlyStoreSkipped(requiredFieldMissing(field), "store '" + id + "': " + message, output);
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

        assertThat(store.id()).isEqualTo("123");
        assertThat(store.addressName()).isEqualTo("true");
        assertThat(store.street2()).isEqualTo("42");
        assertThat(store.street3()).isEqualTo("false");
        assertThat(store.coordinates()).isEqualTo(new Coordinates(52.0907, 5.1214));
    }

    @ParameterizedTest
    @MethodSource("objectOrArrayStringFields")
    void skipsStoresWithObjectsAndArraysInStringFields(String json, String path, CapturedOutput output) {
        assertOnlyStoreSkipped(json, "[\"" + path + "\"]", output);
        assertThat(output).contains("Cannot deserialize value of type `java.lang.String`");
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
    void skipsStoresWithMissingCoordinates(String json, String expectedMessage, CapturedOutput output) {
        assertOnlyStoreSkipped(json, expectedMessage, output);
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
                        """, "Missing required creator property 'latitude'"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": "52.0907"
                        }]}
                        """, "Missing required creator property 'longitude'"),
                Arguments.of("""
                        {"stores": [{
                          "uuid": "seed-a",
                          "addressName": "Jumbo Distinct Name",
                          "city": "Utrecht",
                          "postalCode": "3511 AB",
                          "street": "Voorstraat",
                          "latitude": null,
                          "longitude": "5.1214"
                        }]}
                        """, "[\"latitude\"]"));
    }

    @ParameterizedTest
    @MethodSource("invalidCoordinates")
    void skipsStoresWithUnparseableNonfiniteAndOutOfRangeCoordinates(String field, String value,
                                                                     String expectedMessage, CapturedOutput output) {
        assertOnlyStoreSkipped("""
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
                field.equals("longitude") ? value : "5.1214"), expectedMessage, output);
    }

    static Stream<Arguments> invalidCoordinates() {
        return Stream.of(
                Arguments.of("latitude", "text", "not a valid `double` value"),
                Arguments.of("latitude", "NaN", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "Infinity", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "-Infinity", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "1e309", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "90.01", "latitude must be finite and between -90 and 90"),
                Arguments.of("latitude", "-90.01", "latitude must be finite and between -90 and 90"),
                Arguments.of("longitude", "text", "[\"longitude\"]"),
                Arguments.of("longitude", "NaN", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "Infinity", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "-Infinity", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "1e309", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "180.01", "longitude must be finite and between -180 and 180"),
                Arguments.of("longitude", "-180.01", "longitude must be finite and between -180 and 180"));
    }

    @Test
    void missingResourceFailsEagerlyWithResourceContext() {
        var failure = catchThrowable(
                () -> new JsonStoreRepository(new ClassPathResource("missing-stores-test.json")));

        assertThat(failure).hasMessageContaining("missing-stores-test.json");
        assertThat(failure).cause().isNotNull();
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

        var failure = catchThrowable(() -> new JsonStoreRepository(resource));

        assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreadable seed")
                .hasMessageContaining("read denied");
    }

    @Test
    void actualPackagedProductionResourceLoadsAndMapsKnownFirstStore() {
        var repository = new JsonStoreRepository(new ClassPathResource("stores.json"));
        var stores = repository.findAll();

        assertThat(stores).extracting(store -> store.id()).allSatisfy(id -> assertThat(id).isNotBlank());
        assertThat(stores.stream().map(store -> store.id()).distinct()).hasSize(stores.size());
        var first = stores.getFirst();
        assertThat(first.id()).isEqualTo("EOgKYx4XFiQAAAFJa_YYZ4At");
        assertThat(first.addressName()).isEqualTo("Jumbo 's Gravendeel Gravendeel Centrum");
        assertThat(first.city()).isEqualTo("'s Gravendeel");
        assertThat(first.postalCode()).isEqualTo("3295 BD");
        assertThat(first.street()).isEqualTo("Kerkstraat");
        assertThat(first.street2()).isEqualTo("37");
        assertThat(first.street3()).isEmpty();
        assertThat(first.coordinates()).isEqualTo(new Coordinates(51.778461, 4.615551));
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

    private static void assertOnlyStoreSkipped(String json, String reason, CapturedOutput output) {
        assertThat(catchThrowable(() -> repository(json)))
                .hasMessage("Failed to load stores from Byte array resource [fixture]: no valid stores found");
        assertThat(output).contains("Skipping invalid store at stores[0]: ").contains(reason);
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
