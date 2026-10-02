Feature: Find nearest stores from the JSON snapshot
  Customers can find nearby stores and get the five nearest.
  Results are geographic distances, and coverage does not block searches.

  Background:
    Given a search position at latitude "52" and longitude "5"

  Scenario: The default returns the nearest five, with deterministic ties at an exact store location
    When the nearest stores are requested over HTTP
    Then 5 stores are returned
    And the first 5 fixture stores are in nearest-first ID-tie order
    And the colocated stores "store-a" and "store-b" come first with zero distance

  Scenario: A position outside coverage still finds stores
    Given a search position at latitude "0" and longitude "0"
    When the nearest stores are requested over HTTP
    Then 5 stores are returned

  Scenario: Globally invalid coordinates are rejected
    Given a search position at latitude "91" and longitude "5"
    When the nearest stores are requested over HTTP
    Then the response is a bad-coordinate problem for "latitude"

  Scenario: A required coordinate cannot be omitted
    Given the "longitude" coordinate is omitted
    When the nearest stores are requested over HTTP
    Then the response is a bad-coordinate problem for "longitude"
