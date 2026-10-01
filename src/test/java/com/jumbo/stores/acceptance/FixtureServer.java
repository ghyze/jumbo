package com.jumbo.stores.acceptance;

import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "stores.data.location=classpath:fixtures/nearest-stores.json")
public abstract class FixtureServer {
    @LocalServerPort
    protected int port;
}
