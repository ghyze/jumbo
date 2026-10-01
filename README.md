# Nearest Jumbo stores

A Java 27 / Spring Boot REST application that finds nearby Jumbo stores from the supplied JSON dataset. No database, Docker, or graphical interface is required.

## Prerequisites

- JDK **27**, with `JAVA_HOME` pointing to that JDK and its `bin` directory on `PATH`. Check with `java -version`.
- Internet access for the first Maven Wrapper build to download Maven and dependencies. A separate Maven installation is unnecessary.
- IntelliJ IDEA's HTTP Client, or another HTTP client, for manual requests.

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-27"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS Terminal (with a JDK 27 installation available):

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 27)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
./mvnw spring-boot:run
```

If transfer of the checkout loses Unix executable permissions, run `chmod +x mvnw`. Use a JDK build matching your Mac's architecture. The wrapper has LF line endings; Java resources are read from the classpath, not a machine-specific path.

The server listens on `http://localhost:8080`. Stop it with Ctrl+C.

### Run the packaged application

After `verify`, on Windows:

```powershell
java -jar target\demo-0.0.1-SNAPSHOT.jar
```

On macOS:

```sh
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

The JAR contains the store data and can be launched from any working directory; use the absolute JAR path when launching elsewhere.

## API

```http
GET http://localhost:8080/api/stores/nearest?latitude=52.0907&longitude=5.1214
```

For a terminal request, use `curl.exe` on Windows or `curl` on macOS:

```sh
curl "http://localhost:8080/api/stores/nearest?latitude=52.0907&longitude=5.1214&limit=3"
```

The response is an object with `stores` and `warnings` arrays. Each store contains its ID, name, address components, numeric latitude/longitude, and `distanceKm`. Results are ordered by unrounded geographic distance, then ID for exact ties. Distances use the Haversine formula with mean Earth radius **6,371.0088 km**: they are not road distances or travel times.

Latitude and longitude are required, finite numbers in `[-90, 90]` and `[-180, 180]`. Missing, blank, nonnumeric, or out-of-range coordinates return `400` using `application/problem+json` with `type`, `title`, `status`, `detail`, and `instance`. Unexpected failures return a generic `500` and are logged server-side.

### Result count and configuration

`stores.search.max-results` is both the default count and the application-wide maximum. It defaults to **5**. Change it in application YAML:

```yaml
stores:
  search:
    max-results: 10
```

Or override it at startup (Windows example; use the macOS JAR path there):

```powershell
java -jar target\demo-0.0.1-SNAPSHOT.jar --stores.search.max-results=10
```

| Request `limit` | Behavior |
| --- | --- |
| Omitted | Use the configured count |
| Positive decimal integer below the cap | Use the requested count |
| Above the cap, including very large integers | Use the configured count |
| Blank, nonnumeric, fractional, zero, or negative | Use the configured count and include `INVALID_LIMIT_DEFAULTED` |

Surrounding whitespace is trimmed. No request returns more stores than available. Invalid application configuration fails startup rather than quietly reverting to five. The cap bounds response size; it does not rate-limit requests or eliminate the full distance scan.

### Distance algorithm

`StoreService` receives a `DistanceCalculator` through constructor injection. Its `between` method
returns a finite, nonnegative distance in kilometres. `HaversineDistance` is the default implementation;
the calculation and result ordering are unchanged.

Spring Boot selects the implementation at startup through `stores.distance.algorithm`. For example:

```powershell
java -jar target\demo-0.0.1-SNAPSHOT.jar --stores.distance.algorithm=haversine
```

The same setting can be supplied in application YAML or through the environment variable
`STORES_DISTANCE_ALGORITHM`. Omitting it selects Haversine; an unknown or blank value fails startup
rather than silently falling back.

To add an algorithm, implement `DistanceCalculator` and register a `@Bean` with
`@ConditionalOnProperty(prefix = "stores.distance", name = "algorithm", havingValue = "your-algorithm")`,
following the Haversine bean in `StoreConfiguration`. Reserve `matchIfMissing = true` for Haversine.
Both implementations can be packaged together, with exactly one enabled by the deployment setting.
Only Haversine is currently provided; switching requires the alternative implementation to be included
in the application and a restart, not a change to `StoreService`.

### Coverage warnings

The supplied file has **587 stores**, all with Dutch-format postal codes and coordinates within the approximate European Netherlands coverage box:

- Latitude: **50.7 to 53.6**, inclusive.
- Longitude: **3.2 to 7.3**, inclusive.

Globally valid coordinates outside this box still return `200` and nearest stores, with warning code `OUTSIDE_SUPPORTED_AREA`. The box is a coverage heuristic, not an exact national border or a guarantee that a store is nearby. For example, `(0, 0)` returns results with a warning, while latitude `91` is an error. Multiple warnings may occur together.

The OpenAPI source is [`src/main/resources/openapi/stores.yaml`](src/main/resources/openapi/stores.yaml). Maven generates the REST interfaces and response models during `generate-sources`; generated Java lives under `target` and must not be edited or committed. There is no Swagger UI.

## Manual requests

Open [`http/stores.http`](http/stores.http) in IntelliJ and run individual requests. They cover default/smaller/capped counts, invalid-limit fallback, a known store location, invalid coordinates, and coverage warnings. Embedded assertions check status and relevant response behavior.

The file assumes the default application cap of five. When changing the server configuration, update `configuredMax` at the top of the file too. `baseUrl` can be changed for a different port. The same HTTP file works on Windows and macOS.

Minimal operational health information is available at `/actuator/health`; there is no external monitoring platform.

## Tests

| Scope | Windows | macOS |
| --- | --- | --- |
| Unit and real-HTTP endpoint tests | `.\mvnw.cmd test` | `./mvnw test` |
| All tests, including Cucumber | `.\mvnw.cmd verify` | `./mvnw verify` |

JUnit tests cover domain invariants, numerical edge cases, repository validation/loading, result selection, configuration, mapping, and limit handling. REST Assured exercises the actual embedded server on a random port. Cucumber scenarios exercise the complete HTTP-to-JSON-repository path with deterministic fixtures, not mocked services.

Test data is built through `TestObjects` builders with valid defaults. Tests prefer real objects and small in-memory implementations over mocks. Surefire reports are under `target/surefire-reports`; Failsafe reports are under `target/failsafe-reports`. Cucumber runs during `verify`, not `test`, and also produces reports under `target/cucumber`. Small-dataset and application-configuration variations are covered by the endpoint tests; Cucumber focuses on the shared customer-facing search scenarios.

## Architecture and trade-offs

```text
Request -> handwritten REST adapter implementing generated StoresApi
        -> StoreService -> StoreRepository -> immutable in-memory stores
        -> ranking/coverage result -> response mapping -> HTTP response
```

- A validated immutable `Coordinates` value object keeps latitude and longitude together.
- `Store` owns required-text invariants, so its constructor and builder cannot create stores with null or blank required fields. `JsonStoreRepository` acts as an anti-corruption layer: it validates JSON structure and types, parses coordinate strings, detects duplicate IDs, and constructs domain objects. Domain validation failures retain resource, entry-index, and UUID context; blank-string rules are not duplicated in the repository.
- The read-only repository resembles a database repository but eagerly reads the entire JSON file once during startup. Maven packages only `src/main/resources/stores.json` as a standard classpath resource. Updates require restart.
- Missing or malformed data, invalid required fields/coordinates, duplicate IDs, or an empty dataset fail startup. Unknown metadata is ignored; malformed stores are not silently skipped.
- All seed entries participate regardless of opening hours, collection-point flags, or location type. Optional address components are normalized to empty strings.
- The service computes all distances and sorts them: `O(n log n)` time and `O(n)` temporary space. This is deliberately simple for 587 stores.
- Handwritten mappings keep generated API models and JSON parsing out of the domain. MapStruct is deferred until repetitive mappings justify another annotation processor.

Future optimization options are a bounded heap (`O(n log k)` ranking, but still all distance calculations) or a geographically correct spatial index that reduces candidate distance calculations. Benchmark against the exhaustive implementation before accepting extra complexity, and preserve antimeridian/polar behavior and deterministic ties.

There are no write APIs, database, Docker files, UI, authentication, road-routing service, or spatial index. See [`PLAN.md`](PLAN.md) for the agreed scope and decisions.

## Verification and effort

Verified on Windows with JDK 27 on 30 September 2026:

- `.\mvnw.cmd test` and `.\mvnw.cmd clean verify`: **324 JUnit/endpoint tests and 10 Cucumber scenarios passed**, with no failures, errors, or skips. The clean build regenerated the OpenAPI contract.
- The executable JAR started from outside the checkout, loaded all 587 stores once, and reported healthy. Its packaged resources contain exactly one seed file and no assignment brief.
- Real HTTP checks covered the default count, a configured cap of ten, smaller/capped/invalid limits, error responses, combined warnings, and an exact store location. Nearest-store IDs matched an independent full-dataset spherical-law-of-cosines calculation at four positions.
- All 16 requests in `http/stores.http` were replayed against the packaged server, including its 33 embedded assertions, using a local Node harness. The IntelliJ client itself has not been exercised here.

**macOS verification remains pending** on the available Mac. Run `./mvnw clean verify`, start the packaged JAR, and execute the HTTP requests there before submission. Windows results do not establish macOS verification.

AI assistance was used for planning, implementation, and test generation, with parallel agents assigned separate responsibilities. Human review changed the original proposal to include coverage warnings, application-level count limits, a shared coordinate type, and minimal mocks. The initial uncapped request-limit proposal was replaced with a configured default/cap. Generated output must be assessed through compilation, tests, and real HTTP requests rather than accepted on assertion alone.

Approximately **17 minutes elapsed** for this AI-assisted implementation and Windows validation session, excluding earlier planning/review and subsequent human or Mac checks. This is wall-clock time, not summed parallel-agent effort. Include those additional activities in the final self-report before submission. The assignment's 4-6 hours is guidance, not an AI completion estimate.
