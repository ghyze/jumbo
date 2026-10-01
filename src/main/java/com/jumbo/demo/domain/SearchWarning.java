package com.jumbo.demo.domain;

import static com.jumbo.demo.util.StringUtil.isBlank;

import lombok.Builder;

@Builder
public record SearchWarning(String code, String message) {
    public SearchWarning {
        if (isBlank(code) || isBlank(message)) {
            throw new IllegalArgumentException("warning code and message must not be blank");
        }
    }
}
