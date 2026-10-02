package com.jumbo.stores.acceptance.cucumber;

import com.jumbo.stores.NearestStoresApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest(classes = NearestStoresApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stores.data.location=classpath:fixtures/nearest-stores.json")
public class CucumberSpringContext {
}
