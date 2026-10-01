package com.jumbo.stores.api;

import static org.assertj.core.api.Assertions.assertThat;
import com.jumbo.stores.domain.InvalidCoordinatesException;
import com.jumbo.stores.service.HaversineDistance;
import com.jumbo.stores.service.SearchProperties;
import com.jumbo.stores.service.StoreService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void missingParametersNameTheMissingParameter() throws Exception {
        var response = handler.handleException(
                new MissingServletRequestParameterException("latitude", "Double"), request());
        var problem = assertProblem(response, 400, "Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Required query parameter 'latitude' is missing.");
    }

    @Test
    void bindingErrorsNameTheParameterButDoNotExposeValuesOrCauses() throws Exception {
        var response = handler.handleException(new MethodArgumentTypeMismatchException(
                "sensitive-input", Double.class, "longitude", (MethodParameter) null,
                new IllegalArgumentException("internal-converter-detail")), request());
        var problem = assertProblem(response, 400, "Bad Request");
        assertThat(problem.getDetail())
                .isEqualTo("Query parameter 'longitude' must be a number between -180 and 180.")
                .doesNotContain("sensitive-input", "internal-converter-detail");
    }

    @Test
    void coordinateBoundaryErrorsNameTheInvalidParameter() {
        var problem = assertProblem(handler.handleInvalidCoordinates(
                new InvalidCoordinatesException("latitude", 90), request()), 400, "Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Query parameter 'latitude' must be a number between -90 and 90.");
    }

    @Test
    void generatedParameterConstraintsAreInheritedAndReportedAsBadRequests() throws Exception {
        var properties = new SearchProperties(5);
        var controller = new StoreController(new StoreService(List::of, properties, new HaversineDistance()));
        var method = StoreController.class.getMethod("findNearestStores", Double.class, Double.class, Integer.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables()
                    .validateParameters(controller, method, new Object[] {91.0, 5.0, null});
            assertThat(violations).isNotEmpty();
            var problem = assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 400, "Bad Request");
            assertThat(problem.getDetail()).isEqualTo("Query parameter 'latitude' must be a number between -90 and 90.");
        }
    }

    @Test
    void generatedLimitConstraintsAreReportedAsBadRequests() throws Exception {
        var properties = new SearchProperties(5);
        var controller = new StoreController(new StoreService(List::of, properties, new HaversineDistance()));
        var method = StoreController.class.getMethod("findNearestStores", Double.class, Double.class, Integer.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables()
                    .validateParameters(controller, method, new Object[] {52.0, 5.0, 0});
            assertThat(violations).isNotEmpty();
            var problem = assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 400, "Bad Request");
            assertThat(problem.getDetail()).isEqualTo("Query parameter 'limit' must be a positive integer.");
        }
    }

    @Test
    void unexpectedIllegalArgumentsProduceGenericServerErrors() {
        var problem = assertProblem(handler.handleUnexpected(
                new IllegalArgumentException("internal-repository-detail"), request()),
                500, "Internal Server Error");
        assertThat(problem.getDetail()).contains("try again").doesNotContain("internal-repository-detail");
    }

    @Test
    void otherHttpErrorsPreserveStandardStatusAndHeaders() throws Exception {
        var response = handler.handleException(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")), request());
        var problem = assertProblem(response, 405, "Method Not Allowed");
        assertThat(problem.getDetail()).isEqualTo(
                "The request could not be processed. Check the request URL, method, and accepted media types.");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET);
    }

    private static ServletWebRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/stores/nearest");
        request.setQueryString("latitude=sensitive-input");
        return new ServletWebRequest(request);
    }

    private static ProblemDetail assertProblem(ResponseEntity<Object> response, int status, String title) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        var problem = (ProblemDetail) response.getBody();
        assertThat(problem.getType()).isEqualTo(URI.create("about:blank"));
        assertThat(problem.getTitle()).isEqualTo(title);
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getDetail()).isNotBlank();
        return problem;
    }
}
