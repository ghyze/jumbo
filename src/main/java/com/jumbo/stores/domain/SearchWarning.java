package com.jumbo.stores.domain;

import static com.jumbo.stores.util.StringUtil.isBlank;
import lombok.Builder;

@Builder
public record SearchWarning(String code, String message) {
    public SearchWarning {
        if (isBlank(code) || isBlank(message)) {
            throw new IllegalArgumentException("warning code and message must not be blank");
        }
    }
}
