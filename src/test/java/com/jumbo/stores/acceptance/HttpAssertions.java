package com.jumbo.stores.acceptance;

import static io.restassured.RestAssured.given;
import static io.restassured.config.JsonConfig.jsonConfig;
import static io.restassured.config.RestAssuredConfig.config;
import static io.restassured.path.json.config.JsonPathConfig.NumberReturnType.DOUBLE;
import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(body).containsOnlyKeys("stores", "warnings");
        List<Map<String, Object>> stores = response.jsonPath().getList("stores");
        assertThat(stores).hasSize(count);
        double previous = -1;
        for (Map<String, Object> store : stores) {
            assertThat(store).containsOnlyKeys("id", "addressName", "city", "postalCode", "street", "street2",
                    "street3", "latitude", "longitude", "distanceKm");
            for (String field : List.of("id", "addressName", "city", "postalCode", "street", "street2", "street3")) {
                assertThat(store.get(field)).as(field).isInstanceOf(String.class);
                if (!field.equals("street2") && !field.equals("street3")) {
                    assertThat((String) store.get(field)).as(field).isNotBlank();
                }
            }
            double latitude = number(store, "latitude").doubleValue();
            double longitude = number(store, "longitude").doubleValue();
            double distance = number(store, "distanceKm").doubleValue();
            assertThat(latitude).isFinite().isBetween(-90.0, 90.0);
            assertThat(longitude).isFinite().isBetween(-180.0, 180.0);
            assertThat(distance).isFinite().isGreaterThanOrEqualTo(0);
            assertThat(distance).as("Distances must be ascending").isGreaterThanOrEqualTo(previous);
            previous = distance;
        }
        List<Map<String, Object>> actualWarnings = response.jsonPath().getList("warnings");
        assertThat(actualWarnings).hasSize(warnings.length);
        assertThat(response.jsonPath().getList("warnings.code")).containsOnly(warnings);
        for (Map<String, Object> warning : actualWarnings) {
            assertThat(warning).containsOnlyKeys("code", "message");
            assertThat(warning.get("code")).isInstanceOf(String.class);
            assertThat(warning.get("message")).isInstanceOf(String.class);
            assertThat((String) warning.get("message")).isNotBlank();
        }
    }

    public static void assertFixtureOrder(Response response, int count) {
        assertThat(response.jsonPath().getList("stores.id")).isEqualTo(ORDERED_IDS.subList(0, count));
    }

    public static void assertBadParameter(Response response, String parameter) {
        response.then().statusCode(400).contentType("application/problem+json");
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body).containsKeys("type", "title", "status", "detail", "instance");
        assertThat(body.get("type")).isEqualTo("about:blank");
        assertThat(body.get("title")).isEqualTo("Bad Request");
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("instance")).isEqualTo(PATH);
        assertThat(body.get("detail")).isInstanceOf(String.class);
        String detail = (String) body.get("detail");
        assertThat(detail).isNotBlank();
        assertThat(detail.contains("'" + parameter + "'") || detail.toLowerCase().contains(parameter))
                .as("Problem detail should identify the invalid parameter: %s", detail)
                .isTrue();
        assertThat(body).doesNotContainKeys("trace", "exception");
    }

    public static void assertBadCoordinates(Response response, String parameter) {
        assertBadParameter(response, parameter);
    }

    public static void assertBadLimit(Response response) {
        assertBadParameter(response, "limit");
    }

    private static Number number(Map<String, Object> store, String field) {
        assertThat(store.get(field)).as(field).isInstanceOf(Number.class);
        return (Number) store.get(field);
    }
}
