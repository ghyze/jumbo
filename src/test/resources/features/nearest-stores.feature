Feature: Find nearest stores from the JSON snapshot
  Customers can find nearby stores with a bounded result count.
  Results are geographic distances, and coverage warnings do not block searches.

  Background:
    Given a search position at latitude "52" and longitude "5"

  Scenario: The default returns the nearest five, with deterministic ties at an exact store location
    When the nearest stores are requested over HTTP
    Then 5 stores are returned without warnings
    And the first 5 fixture stores are in nearest-first ID-tie order
    And the colocated stores "store-a" and "store-b" come first with zero distance

  Scenario Outline: A positive count is respected up to the application cap
    Given a requested limit of "<limit>"
    When the nearest stores are requested over HTTP
    Then <count> stores are returned without warnings
    And the first <count> fixture stores are in nearest-first ID-tie order

    Examples:
      | limit | count |
      | 3     | 3     |
      | 20    | 5     |

  Scenario Outline: Invalid supplied limits are rejected
    Given a requested limit of "<limit>"
    When the nearest stores are requested over HTTP
    Then the response is a bad-limit problem

    Examples:
      | limit |
      | many  |
      | 0     |

  Scenario: A position outside coverage still finds stores
    Given a search position at latitude "0" and longitude "0"
    When the nearest stores are requested over HTTP
    Then 5 stores are returned with warning "OUTSIDE_SUPPORTED_AREA"

  Scenario: Globally invalid coordinates are rejected instead of returning a coverage warning
    Given a search position at latitude "91" and longitude "5"
    When the nearest stores are requested over HTTP
    Then the response is a bad-coordinate problem for "latitude"

  Scenario: A required coordinate cannot be omitted
    Given the "longitude" coordinate is omitted
    When the nearest stores are requested over HTTP
    Then the response is a bad-coordinate problem for "longitude"
