package com.jumbo.stores.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stores.search")
public record SearchProperties(@DefaultValue("5") int maxResults) {
    public SearchProperties {
        if (maxResults < 1) {
            throw new IllegalArgumentException("stores.search.max-results must be at least 1");
        }
    }
}
