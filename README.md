# Nearest Jumbo stores

Java 27 / Spring Boot 4.1.1 REST API that returns the nearest Jumbo stores to a supplied latitude and longitude. It is intentionally small: the application loads the supplied JSON file once at startup, keeps it in memory, and exposes one read-only endpoint. No database, Docker, UI, or globally installed Maven is required.

Repository: <https://github.com/ghyze/jumbo>

## Prerequisites

- JDK **27** with `JAVA_HOME` pointing to that JDK and its `bin` directory on `PATH`.
- Internet access for the first Maven Wrapper run, so Maven and dependencies can be downloaded.
- IntelliJ IDEA's HTTP Client, `curl`, or another HTTP client for manual requests.

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-27"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

macOS Terminal:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 27)
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean verify
./mvnw spring-boot:run
```

If transfer of the checkout loses Unix executable permissions, run `chmod +x mvnw`. The server listens on `http://localhost:8080`; stop it with Ctrl+C.

## Running the packaged application

After `clean verify`, start the executable JAR:

```powershell
java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar
```

On macOS use `target/nearest-stores-0.0.1-SNAPSHOT.jar`. The JAR contains `stores.json` and can be launched from any working directory if you pass the absolute JAR path.

## API

```http
GET http://localhost:8080/api/stores/nearest?latitude=52.0907&longitude=5.1214&limit=3
```

The response is a JSON object with `stores` and `warnings` arrays. Each store includes the seed UUID as `id`, address fields, numeric latitude/longitude, and `distanceKm`. Results are ordered by unrounded Haversine distance, then ID for exact ties. Distances use mean Earth radius **6,371.0088 km** and are not road distances or travel times.

Latitude and longitude are required finite numbers in `[-90, 90]` and `[-180, 180]`. Missing, blank, nonnumeric, non-finite, or out-of-range coordinates return `400 application/problem+json` with `type`, `title`, `status`, `detail`, and `instance`. Unexpected failures return a generic `500` and are logged server-side.

### Result count and configuration

`stores.search.max-results` is both the default result count and the maximum any request can return. It defaults to **5**. Override it in YAML or at startup:

```yaml
stores:
  search:
    max-results: 10
```

```powershell
java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar --stores.search.max-results=10
```

| Request `limit` | Behavior |
| --- | --- |
| Omitted or blank | Use the configured count |
| Positive integer below the cap | Use the requested count |
| Positive integer above the cap | Silently cap at the configured count |
| Nonnumeric, fractional, zero, negative, or beyond the 32-bit integer range | Return `400 Bad Request` naming `limit` |

No response returns more stores than available. Invalid application configuration fails startup. The cap bounds response size; it is not request rate limiting.

### Store data and coverage

The default data location is `classpath:stores.json`; override it with a Spring resource location such as:

```powershell
java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar --stores.data.location=file:C:\data\stores.json
```

The loader uses Jackson data binding with default coercion: unknown fields are ignored, numbers and booleans in text fields become strings, and objects or arrays in text fields fail startup. Missing or malformed data, invalid required fields or coordinates, duplicate IDs, and empty datasets fail startup.

The supplied dataset has 587 stores with Dutch-format postal codes. The application treats latitude **50.7 to 53.6** and longitude **3.2 to 7.3** as an approximate coverage box for the European Netherlands. Valid coordinates outside that box still return nearest stores with warning code `OUTSIDE_SUPPORTED_AREA`; invalid global coordinates are rejected.

The OpenAPI source is [`src/main/resources/openapi/stores.yaml`](src/main/resources/openapi/stores.yaml). Maven generates API interfaces and response models under `target/generated-sources/openapi`; generated Java is not edited or committed. There is no Swagger UI.

## Manual requests

Open [`http/stores.http`](http/stores.http) in IntelliJ and run individual requests. It covers default, smaller, capped, invalid-limit, outside-coverage, missing-coordinate, invalid-coordinate, known-store, and health requests. The file assumes the default cap of five; update `configuredMax` if you override `stores.search.max-results`. Minimal operational health is available at `/actuator/health`.

## Tests

| Scope | Windows | macOS |
| --- | --- | --- |
| Unit and real-HTTP endpoint tests | `.\mvnw.cmd test` | `./mvnw test` |
| Full verification, including Cucumber | `.\mvnw.cmd clean verify` | `./mvnw clean verify` |

JUnit covers domain invariants, distance calculation, repository loading and validation, configuration, result selection, HTTP edge cases, and response mapping. REST Assured exercises the embedded server on a random port. Cucumber covers the business-level request-to-repository scenarios with deterministic fixtures. Reports are under `target/surefire-reports`, `target/failsafe-reports`, and `target/cucumber`.

## Architecture

```text
api         StoreController, ApiExceptionHandler, response mapping, generated API models
 │
service     StoreService, SearchProperties, DistanceCalculator, HaversineDistance
 │
repository  StoreRepository, JsonStoreRepository
 │
domain      Coordinates, Store, NearestStore, SearchResult, SearchWarning, WarningCode, CoverageArea

config      StoreConfiguration and StoreDataProperties wire beans only
```

Requests enter the handwritten `StoreController`, which implements the generated OpenAPI interface, converts inputs to domain values, delegates to `StoreService`, and maps domain results back to generated response models. The service applies the default/cap, calculates distances for all stores, sorts by distance then ID, selects the requested count, and adds any coverage warning. The repository owns JSON parsing and publishes an immutable in-memory snapshot.

This full scan and sort is `O(n log n)` and deliberately simple for 587 stores. Updates to the dataset require restart. Generated HTTP models stay at the API boundary; JSON deserialization stays in the repository; business rules live in service or domain.

## Design decisions

- **D1 — Strict invalid `limit`:** invalid supplied limits return `400`; valid values above the cap are capped. This reverses the forgiving fallback in `PLAN.md` §1 because client input errors are clearer than hidden defaults and warning codes.
- **D2 — No distance-algorithm switch:** the Haversine implementation is wired directly while keeping the `DistanceCalculator` seam for tests and future alternatives.
- **D3 — Keep Lombok:** limited use of Lombok avoids boilerplate without adding new architectural concepts.
- **D4 — Rename Initializr placeholders:** artifact, application class, controller, package, and Spring application name use `nearest-stores` / `com.jumbo.stores` to look submission-ready.
- **D5 — Split test ownership:** Cucumber describes business scenarios; REST Assured focuses on HTTP edge cases, avoiding duplicated coverage.
- **D6 — Jackson data binding:** binding replaces hand-written tree walking; Jackson's default text coercion is accepted and documented.

## With more time

- Add request/response validation against the OpenAPI contract if the validator fits Spring Boot 4/Jackson 3 cleanly.
- Introduce NullAway or package-level nullness only as a deliberate project-wide cleanup.
- Benchmark a bounded heap or spatial index only if the dataset or traffic grows enough to justify the complexity.
- Add CI for Windows and macOS verification.

## Verification, effort, and AI usage

Windows verification before submission:

- `.\mvnw.cmd clean verify` completed on Windows with JDK 27 during the final documentation pass.

<!-- TODO(candidate): Replace this section with the total time spent self-report required by the brief. Include planning, review, implementation, validation, and any manual follow-up. -->

<!-- TODO(candidate): Record the macOS verification result here after running `./mvnw clean verify`, starting the packaged JAR, and exercising the HTTP requests on macOS. -->

Draft AI-usage note: GitHub Copilot CLI was used to review the project, write an incremental improvement plan with explicit decisions taken by the candidate, and implement the steps, partly with parallel sub-agents in separate git worktrees. Each implementation step was validated with the Maven build covering unit, HTTP, and Cucumber tests, and final behavior was checked against the packaged JAR and real HTTP requests.

<!-- TODO(candidate): Add personal AI-usage reflections: where you disagreed with suggestions, what you changed, and how you reviewed the generated output. -->
