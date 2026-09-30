package com.jumbo.demo.api;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String INVALID_COORDINATES =
            "Provide both latitude and longitude as finite numbers: latitude must be between -90 and 90, "
                    + "and longitude between -180 and 180, inclusive.";
    private static final String UNEXPECTED_ERROR =
            "An unexpected server error occurred. Please try again later.";

    @ExceptionHandler(StoreRestAdapter.InvalidCoordinatesException.class)
    public ResponseEntity<Object> handleInvalidCoordinates(
            StoreRestAdapter.InvalidCoordinatesException exception, WebRequest request) {
        return handleExceptionInternal(exception, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException exception, WebRequest request) {
        boolean returnValueViolation = exception.getConstraintViolations().stream()
                .anyMatch(violation -> {
                    for (var node : violation.getPropertyPath()) {
                        if (node.getKind() == ElementKind.RETURN_VALUE) {
                            return true;
                        }
                    }
                    return false;
                });
        var status = returnValueViolation ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        return handleExceptionInternal(exception, null, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        return handleExceptionInternal(
                exception, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail;
        if (status.is5xxServerError()) {
            logger.error("Unexpected failure while handling an HTTP request", exception);
            detail = UNEXPECTED_ERROR;
        } else if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            detail = INVALID_COORDINATES;
        } else {
            detail = "The request could not be processed. Check the request URL, method, and accepted media types.";
        }
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        var httpStatus = HttpStatus.resolve(status.value());
        problem.setTitle(httpStatus == null ? "HTTP error" : httpStatus.getReasonPhrase());
        problem.setInstance(URI.create(request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI() : "/"));
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(exception, problem, responseHeaders, status, request);
    }
}
