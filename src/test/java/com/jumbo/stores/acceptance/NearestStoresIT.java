package com.jumbo.stores.acceptance;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/nearest-stores.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.jumbo.stores.acceptance.cucumber")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty,json:target/cucumber/nearest-stores.json,html:target/cucumber/nearest-stores.html")
@ConfigurationParameter(key = "cucumber.junit-platform.naming-strategy", value = "long")
@ConfigurationParameter(key = "cucumber.junit-platform.naming-strategy.long.example-name", value = "pickle")
public class NearestStoresIT {
}
