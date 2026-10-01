package com.jumbo.demo.acceptance;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.DOUBLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HttpAssertions {
    public static final String PATH = "/api/stores/nearest";
    public static final List<String> ORDERED_IDS = List.of(
            "store-a", "store-b", "store-c", "store-d", "store-e", "store-f",
            "store-g", "store-h", "store-i", "store-j", "store-k", "store-l");

    private HttpAssertions() {
    }

    public static Response search(int port, Map<String, ?> parameters) {
        return given().config(config().jsonConfig(jsonConfig().numberReturnType(DOUBLE)))
                .baseUri("http://localhost").port(port).queryParams(parameters)
                .when().get(PATH);
    }

    public static void assertSuccess(Response response, int count, String... warnings) {
        response.then().statusCode(200).contentType("application/json");
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertEquals(Set.of("stores", "warnings"), body.keySet());
        List<Map<String, Object>> stores = response.jsonPath().getList("stores");
        assertEquals(count, stores.size());
        double previous = -1;
        for (Map<String, Object> store : stores) {
            assertEquals(Set.of("id", "addressName", "city", "postalCode", "street", "street2",
                    "street3", "latitude", "longitude", "distanceKm"), store.keySet());
            for (String field : List.of("id", "addressName", "city", "postalCode", "street",
                    "street2", "street3")) {
                assertInstanceOf(String.class, store.get(field), field);
                if (!field.equals("street2") && !field.equals("street3")) {
                    assertFalse(((String) store.get(field)).isBlank(), field);
                }
            }
            double latitude = assertInstanceOf(Number.class, store.get("latitude")).doubleValue();
            double longitude = assertInstanceOf(Number.class, store.get("longitude")).doubleValue();
            double distance = assertInstanceOf(Number.class, store.get("distanceKm")).doubleValue();
            assertTrue(Double.isFinite(latitude) && latitude >= -90 && latitude <= 90);
            assertTrue(Double.isFinite(longitude) && longitude >= -180 && longitude <= 180);
            assertTrue(Double.isFinite(distance) && distance >= 0);
            assertTrue(distance >= previous, "Distances must be ascending");
            previous = distance;
        }
        List<Map<String, Object>> actualWarnings = response.jsonPath().getList("warnings");
        assertEquals(warnings.length, actualWarnings.size());
        assertEquals(Set.of(warnings), Set.copyOf(response.jsonPath().getList("warnings.code")));
        for (Map<String, Object> warning : actualWarnings) {
            assertEquals(Set.of("code", "message"), warning.keySet());
            assertInstanceOf(String.class, warning.get("code"));
            assertFalse(assertInstanceOf(String.class, warning.get("message")).isBlank());
        }
    }

    public static void assertFixtureOrder(Response response, int count) {
        assertEquals(ORDERED_IDS.subList(0, count), response.jsonPath().getList("stores.id"));
    }

    public static void assertBadCoordinates(Response response) {
        response.then().statusCode(400).contentType("application/problem+json");
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertTrue(body.keySet().containsAll(Set.of("type", "title", "status", "detail", "instance")));
        assertEquals("about:blank", body.get("type"));
        assertEquals("Bad Request", body.get("title"));
        assertEquals(400, body.get("status"));
        assertEquals(PATH, body.get("instance"));
        String detail = assertInstanceOf(String.class, body.get("detail"));
        assertFalse(detail.isBlank());
        assertTrue(detail.toLowerCase().contains("latitude") || detail.toLowerCase().contains("longitude"),
                "Coordinate errors should identify the invalid parameter: " + detail);
        assertFalse(body.containsKey("trace"));
        assertFalse(body.containsKey("exception"));
    }
}
