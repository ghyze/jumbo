package com.jumbo.stores.acceptance.cucumber;

import static com.jumbo.stores.acceptance.HttpAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;

public class NearestStoresSteps {
    @LocalServerPort
    private int port;

    private Map<String, String> parameters;
    private Response response;

    @Before
    public void resetRequest() {
        parameters = new LinkedHashMap<>();
        response = null;
    }

    @Given("a search position at latitude {string} and longitude {string}")
    public void position(String latitude, String longitude) {
        parameters.put("latitude", latitude);
        parameters.put("longitude", longitude);
    }

    @Given("the {string} coordinate is omitted")
    public void omittedCoordinate(String coordinate) {
        parameters.remove(coordinate);
    }

    @When("the nearest stores are requested over HTTP")
    public void request() {
        response = search(port, parameters);
    }

    @Then("{int} stores are returned without warnings")
    public void success(int count) {
        assertThat(response).isNotNull();
        assertSuccess(response, count);
    }

    @Then("{int} stores are returned with warning {string}")
    public void warning(int count, String warning) {
        assertSuccess(response, count, warning);
    }

    @Then("{int} stores are returned with warnings {string} and {string}")
    public void combinedWarnings(int count, String first, String second) {
        assertSuccess(response, count, first, second);
    }

    @Then("the first {int} fixture stores are in nearest-first ID-tie order")
    public void order(int count) {
        assertFixtureOrder(response, count);
    }

    @Then("the colocated stores {string} and {string} come first with zero distance")
    public void exactLocation(String first, String second) {
        assertThat(response.jsonPath().getString("stores[0].id")).isEqualTo(first);
        assertThat(response.jsonPath().getString("stores[1].id")).isEqualTo(second);
        assertThat(response.jsonPath().getDouble("stores[0].distanceKm")).isCloseTo(0, within(0.0));
        assertThat(response.jsonPath().getDouble("stores[1].distanceKm")).isCloseTo(0, within(0.0));
    }

    @Then("the response is a bad-coordinate problem for {string}")
    public void badCoordinates(String parameter) {
        assertBadCoordinates(response, parameter);
    }
}
