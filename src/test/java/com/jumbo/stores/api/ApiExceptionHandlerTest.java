package com.jumbo.stores.api;

import static com.jumbo.stores.util.StringUtil.isBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jumbo.stores.config.SearchProperties;
import com.jumbo.stores.service.HaversineDistance;
import com.jumbo.stores.service.StoreService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void missingParametersUseTheCompleteProblemShape() throws Exception {
        var response = handler.handleException(
                new MissingServletRequestParameterException("latitude", "Double"), request());
        var problem = assertProblem(response, 400, "Bad Request");
        assertTrue(problem.getDetail().contains("latitude"));
        assertTrue(problem.getDetail().contains("longitude"));
        assertTrue(problem.getDetail().contains("finite"));
    }

    @Test
    void bindingErrorsDoNotExposeValuesOrCauses() throws Exception {
        var response = handler.handleException(new TypeMismatchException(
                "sensitive-input", Double.class, new IllegalArgumentException("internal-converter-detail")),
                request());
        var problem = assertProblem(response, 400, "Bad Request");
        assertFalse(problem.getDetail().contains("sensitive-input"));
        assertFalse(problem.getDetail().contains("internal-converter-detail"));
        assertFalse(problem.getDetail().contains("Double"));
    }

    @Test
    void coordinateBoundaryErrorsUseTheSameProblemShape() {
        assertProblem(handler.handleInvalidCoordinates(
                new StoreController.InvalidCoordinatesException(), request()), 400, "Bad Request");
    }

    @Test
    void generatedParameterConstraintsAreInheritedAndReportedAsBadRequests() throws Exception {
        var properties = new SearchProperties(5);
        var controller = new StoreController(new StoreService(List::of, properties, new HaversineDistance()), properties);
        var method = StoreController.class.getMethod("findNearestStores", Double.class, Double.class, String.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables()
                    .validateParameters(controller, method, new Object[] {91.0, 5.0, null});
            assertFalse(violations.isEmpty());
            assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 400, "Bad Request");
        }
    }

    @Test
    void invalidReturnValuesAreServerErrorsNotClientErrors() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables().validateReturnValue(
                    new InvalidReturnValue(), InvalidReturnValue.class.getMethod("value"), null);
            assertFalse(violations.isEmpty());
            var problem = assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 500, "Internal Server Error");
            assertFalse(problem.getDetail().contains("must not be null"));
        }
    }

    @Test
    void unexpectedIllegalArgumentsProduceGenericServerErrors() {
        var problem = assertProblem(handler.handleUnexpected(
                new IllegalArgumentException("internal-repository-detail"), request()),
                500, "Internal Server Error");
        assertTrue(problem.getDetail().contains("try again"));
        assertFalse(problem.getDetail().contains("internal-repository-detail"));
    }

    @Test
    void otherHttpErrorsPreserveStandardStatusAndHeaders() throws Exception {
        var response = handler.handleException(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET")), request());
        assertProblem(response, 405, "Method Not Allowed");
        assertTrue(response.getHeaders().getAllow().contains(HttpMethod.GET));
    }

    private static ServletWebRequest request() {
        var request = new MockHttpServletRequest("GET", "/api/stores/nearest");
        request.setQueryString("latitude=sensitive-input");
        return new ServletWebRequest(request);
    }

    private static ProblemDetail assertProblem(ResponseEntity<Object> response, int status, String title) {
        assertEquals(status, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        var problem = assertInstanceOf(ProblemDetail.class, response.getBody());
        assertEquals(URI.create("about:blank"), problem.getType());
        assertEquals(title, problem.getTitle());
        assertEquals(status, problem.getStatus());
        assertFalse(isBlank(problem.getDetail()));
        assertEquals(URI.create("/api/stores/nearest"), problem.getInstance());
        return problem;
    }

    public static class InvalidReturnValue {
        @NotNull
        public String value() {
            return null;
        }
    }
}
