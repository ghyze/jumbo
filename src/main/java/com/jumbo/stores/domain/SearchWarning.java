package com.jumbo.stores.domain;

import static com.jumbo.stores.util.StringUtil.isBlank;
import java.util.Objects;
import lombok.Builder;

@Builder
public record SearchWarning(WarningCode code, String message) {
    public SearchWarning {
        Objects.requireNonNull(code, "code");
        if (isBlank(message)) {
            throw new IllegalArgumentException("warning message must not be blank");
        }
    }
}
