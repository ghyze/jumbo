package com.jumbo.stores.acceptance.cucumber;

import com.jumbo.stores.NearestStoresApplication;
import com.jumbo.stores.acceptance.FixtureServer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringContext extends FixtureServer {
}
