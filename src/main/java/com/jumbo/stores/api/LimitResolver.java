package com.jumbo.stores.api;

import com.jumbo.stores.config.SearchProperties;

final class LimitResolver {
    private final int maximum;

    LimitResolver(SearchProperties properties) {
        maximum = properties.maxResults();
    }

    Resolution resolve(String rawLimit) {
        if (rawLimit == null) {
            return new Resolution(maximum, false);
        }
        String limit = rawLimit.strip();
        int value = 0;
        boolean positive = false;
        for (int index = 0; index < limit.length(); index++) {
            char digit = limit.charAt(index);
            if (digit < '0' || digit > '9') {
                return new Resolution(maximum, true);
            }
            int number = digit - '0';
            positive |= number != 0;
            // Saturate before multiplication, but still validate every remaining character.
            if (value > maximum / 10 || (value == maximum / 10 && number > maximum % 10)) {
                value = maximum;
            } else {
                value = value * 10 + number;
            }
        }
        return positive ? new Resolution(value, false) : new Resolution(maximum, true);
    }

    record Resolution(int count, boolean defaulted) {
    }
}
