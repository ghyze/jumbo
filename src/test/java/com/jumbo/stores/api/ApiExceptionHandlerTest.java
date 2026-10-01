package com.jumbo.stores.api;

import static com.jumbo.stores.util.StringUtil.isBlank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        assertEquals("Required query parameter 'latitude' is missing.", problem.getDetail());
    }

    @Test
    void bindingErrorsNameTheParameterButDoNotExposeValuesOrCauses() throws Exception {
        var response = handler.handleException(new MethodArgumentTypeMismatchException(
                "sensitive-input", Double.class, "longitude", (MethodParameter) null,
                new IllegalArgumentException("internal-converter-detail")), request());
        var problem = assertProblem(response, 400, "Bad Request");
        assertEquals("Query parameter 'longitude' must be a number between -180 and 180.", problem.getDetail());
        assertFalse(problem.getDetail().contains("sensitive-input"));
        assertFalse(problem.getDetail().contains("internal-converter-detail"));
    }

    @Test
    void coordinateBoundaryErrorsNameTheInvalidParameter() {
        var problem = assertProblem(handler.handleInvalidCoordinates(
                new InvalidCoordinatesException("latitude"), request()), 400, "Bad Request");
        assertEquals("Query parameter 'latitude' must be a number between -90 and 90.", problem.getDetail());
    }

    @Test
    void generatedParameterConstraintsAreInheritedAndReportedAsBadRequests() throws Exception {
        var properties = new SearchProperties(5);
        var controller = new StoreController(new StoreService(List::of, properties, new HaversineDistance()));
        var method = StoreController.class.getMethod("findNearestStores", Double.class, Double.class, Integer.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().forExecutables()
                    .validateParameters(controller, method, new Object[] {91.0, 5.0, null});
            assertFalse(violations.isEmpty());
            var problem = assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 400, "Bad Request");
            assertEquals("Query parameter 'latitude' must be a number between -90 and 90.", problem.getDetail());
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
            assertFalse(violations.isEmpty());
            var problem = assertProblem(handler.handleConstraintViolation(
                    new ConstraintViolationException(violations), request()), 400, "Bad Request");
            assertEquals("Query parameter 'limit' must be a positive integer.", problem.getDetail());
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
        var problem = assertProblem(response, 405, "Method Not Allowed");
        assertEquals("The request could not be processed. Check the request URL, method, and accepted media types.",
                problem.getDetail());
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
        return problem;
    }
}
