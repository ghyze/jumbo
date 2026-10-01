package com.jumbo.demo.acceptance.cucumber;

import com.jumbo.demo.NearestStoresApplication;
import com.jumbo.demo.acceptance.FixtureServer;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringContext extends FixtureServer {
}
