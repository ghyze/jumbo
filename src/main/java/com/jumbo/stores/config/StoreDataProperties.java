package com.jumbo.stores.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties("stores.data")
public record StoreDataProperties(@DefaultValue("classpath:stores.json") Resource location) {
}
