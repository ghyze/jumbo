package com.jumbo.stores.domain;

public class InvalidCoordinatesException extends IllegalArgumentException {
    private final String field;

    public InvalidCoordinatesException(String field) {
        super("Invalid coordinate: " + field);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
