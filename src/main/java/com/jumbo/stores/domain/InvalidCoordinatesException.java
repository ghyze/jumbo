package com.jumbo.stores.domain;

public class InvalidCoordinatesException extends IllegalArgumentException {
    private final String field;

    public InvalidCoordinatesException(String field, int bound) {
        super("%s must be finite and between -%d and %d".formatted(field, bound, bound));
        this.field = field;
    }

    public String field() {
        return field;
    }
}