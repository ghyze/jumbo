package com.jumbo.demo.acceptance;

import com.jumbo.demo.repository.StoreRepository;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;

public abstract class FixtureServer {
    @TestBean(name = "storeRepository", enforceOverride = true,
            methodName = "com.jumbo.demo.acceptance.FixtureRepositories#twelveStores")
    protected StoreRepository storeRepository;

    @LocalServerPort
    protected int port;
}
