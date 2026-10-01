package com.jumbo.stores.domain;

import java.util.Objects;
import lombok.Builder;

@Builder
public record SearchWarning(WarningCode code, String message) {
    public SearchWarning {
        Objects.requireNonNull(code, "code");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("warning message must not be blank");
        }
    }
}
