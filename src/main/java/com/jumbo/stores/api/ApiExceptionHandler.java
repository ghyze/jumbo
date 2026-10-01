package com.jumbo.stores.api;

import com.jumbo.stores.domain.InvalidCoordinatesException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String GENERIC_CLIENT_ERROR =
            "The request could not be processed. Check the request URL, method, and accepted media types.";
    private static final Map<String, String> INVALID_PARAMETER_DETAILS = Map.of(
            "latitude", "Query parameter 'latitude' must be a number between -90 and 90.",
            "longitude", "Query parameter 'longitude' must be a number between -180 and 180.");
    private static final String UNEXPECTED_ERROR =
            "An unexpected server error occurred. Please try again later.";

    @ExceptionHandler(InvalidCoordinatesException.class)
    public ResponseEntity<Object> handleInvalidCoordinates(
            InvalidCoordinatesException exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        logger.error("Unexpected failure while handling an HTTP request", exception);
        return handleExceptionInternal(
                exception, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detailFor(exception, status));
        problem.setType(URI.create("about:blank"));
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(exception, problem, responseHeaders, status, request);
    }

    private static String detailFor(Exception exception, HttpStatusCode status) {
        return switch (exception) {
            case MissingServletRequestParameterException e ->
                    "Required query parameter '%s' is missing.".formatted(e.getParameterName());
            case MethodArgumentTypeMismatchException e -> invalidParameter(e.getName());
            case ConstraintViolationException e -> invalidParameter(parameterName(e));
            case InvalidCoordinatesException e -> invalidParameter(e.field());
            default -> status.is5xxServerError() ? UNEXPECTED_ERROR : GENERIC_CLIENT_ERROR;
        };
    }

    private static String invalidParameter(String parameterName) {
        return INVALID_PARAMETER_DETAILS.getOrDefault(parameterName, GENERIC_CLIENT_ERROR);
    }

    private static String parameterName(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .flatMap(violation -> {
                    var nodes = new java.util.ArrayList<String>();
                    for (var node : violation.getPropertyPath()) {
                        if (node.getKind() == ElementKind.PARAMETER) {
                            nodes.add(node.getName());
                        }
                    }
                    return nodes.stream();
                })
                .findFirst()
                .orElse("");
    }
}
