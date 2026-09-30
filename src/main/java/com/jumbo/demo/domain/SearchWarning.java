package com.jumbo.demo.domain;

import lombok.Builder;

@Builder
public record SearchWarning(String code, String message) {
    public SearchWarning {
        if (code == null || code.isBlank() || message == null || message.isBlank()) {
            throw new IllegalArgumentException("warning code and message must not be blank");
        }
    }
}
