package com.jumbo.stores.config;

import static com.jumbo.stores.util.StringUtil.isBlank;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("stores.search")
public final class SearchProperties {
    @Min(1)
    private final int maxResults;

    @ConstructorBinding
    public SearchProperties(@DefaultValue("5") String maxResults) {
        this(parseMaxResults(maxResults));
    }

    public SearchProperties(int maxResults) {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("stores.search.max-results must be a positive Java integer");
        }
        this.maxResults = maxResults;
    }

    public int maxResults() {
        return maxResults;
    }

    private static int parseMaxResults(String value) {
        // Bind as text so an explicitly blank value cannot silently use the default.
        if (isBlank(value) || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException("stores.search.max-results must be a positive Java integer");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("stores.search.max-results must be a positive Java integer", exception);
        }
    }
}
