package com.jumbo.stores.domain;

public class InvalidCoordinatesException extends IllegalArgumentException {
    private final String field;

    public InvalidCoordinatesException(String field, int limit) {
        super("%s must be finite and between -%d and %d".formatted(field, limit, limit));
        this.field = field;
    }

    public String field() {
        return field;
    }
}