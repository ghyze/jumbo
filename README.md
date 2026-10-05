# Nearest Jumbo stores

Java 27 / Spring Boot 4.1.1 REST API that returns the nearest Jumbo stores to a supplied latitude and longitude.
It is intentionally small: the application loads the supplied JSON file once at startup, keeps it in memory, and exposes
one read-only endpoint. No database, Docker, UI, or globally installed Maven is required.

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

If transfer of the checkout loses Unix executable permissions, run `chmod +x mvnw`.
The server listens on `http://localhost:8080`, stop it with Ctrl+C.

## Running the packaged application

After `clean verify`, start the executable JAR:

```powershell
java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar
```

On macOS use `target/nearest-stores-0.0.1-SNAPSHOT.jar`. The JAR contains `stores.json` and can be launched from any
working directory if you pass the absolute JAR path.

## API

```http
GET http://localhost:8080/api/stores/nearest?latitude=52.0907&longitude=5.1214
```

The response is a JSON object with a `stores` array. Each store includes the seed UUID as `id`, address fields, numeric
latitude/longitude, and `distanceKm`. Results are ordered by distance, then ID for exact ties.

Latitude and longitude are required finite numbers in `[-90, 90]` and `[-180, 180]`. Missing, blank, nonnumeric,
non-finite, or out-of-range coordinates return `400 application/problem+json` with `type`, `title`, `status`, `detail`,
and `instance`. Unexpected failures return a generic `500` and are logged server-side.

### Result count

Every request returns the 5 nearest stores. Fewer are returned only when the dataset is smaller.

### Store data and coverage

The default data location is `classpath:stores.json`; override it with a Spring resource location such as:

```powershell
java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar --stores.data.location=file:C:\data\stores.json
```

The loader uses Jackson data binding. A store entry with invalid data is skipped with a WARN log that names its index
and the reason, and the remaining stores still load. Invalid data includes missing or blank required fields, objects or
arrays in text fields, missing or invalid coordinates, and a duplicate ID (the first occurrence is kept). Startup fails
only when the file is missing, unreadable or not well-formed JSON, or when it contains no valid stores.

The supplied seed file currently contains **587** stores in `src/main/resources/stores.json`.

The application treats latitude **50.7 to 53.6** and longitude **3.2 to 7.3** as an approximate coverage box for
the Netherlands. Valid coordinates outside that box still return nearest stores, but the service writes a WARN log
because the results may be far away. Invalid global coordinates are rejected.

The OpenAPI source is [`src/main/resources/openapi/stores.yaml`](src/main/resources/openapi/stores.yaml). Maven generates API interfaces and response
models under `target/generated-sources/openapi`. Generated Java is not edited or committed.

## Manual requests

Open [`http/stores.http`](http/stores.http) in IntelliJ and run individual requests. It covers default count, outside-coverage,
missing-coordinate, invalid-coordinate, known-store, and health requests. Each request also has an equivalent `curl`
command in a comment, for use without IntelliJ. For example:

```shell
curl -i "http://localhost:8080/api/stores/nearest?latitude=52.0907&longitude=5.1214"
```

On Windows PowerShell 5, type `curl.exe` instead of `curl`, because `curl` is an alias for `Invoke-WebRequest` there.

Minimal operational health is available at `/actuator/health`.

## Tests

| Scope | Windows | macOS |
| --- | --- | --- |
| Unit and real-HTTP endpoint tests | `.\mvnw.cmd test` | `./mvnw test` |
| Full verification, including Cucumber | `.\mvnw.cmd clean verify` | `./mvnw clean verify` |

JUnit covers domain invariants, distance calculation, repository loading and validation, result selection,
HTTP edge cases, and response mapping. REST Assured exercises the embedded server on a random port.
Cucumber covers the business-level request-to-repository scenarios with deterministic fixtures.
Reports are under `target/surefire-reports`, `target/failsafe-reports`, and `target/cucumber`.

## Architecture

```text
api         StoreController, ApiExceptionHandler, response mapping, generated API models
 │
service     StoreService, DistanceCalculator, HaversineDistance
 │
repository  StoreRepository, JsonStoreRepository
 │
domain      Coordinates, Store, NearestStore, CoverageArea

config      StoreConfiguration and StoreDataProperties wire beans only
```

Requests enter the `StoreController`, which implements the generated OpenAPI interface, converts inputs to domain values,
delegates to `StoreService`, and maps nearest stores back to generated response models. The service logs out-of-coverage
searches, calculates distances for all stores, sorts by distance then ID, and selects the five nearest. The repository
owns JSON parsing and publishes an immutable in-memory snapshot.

Updates to the dataset require restart. Generated HTTP models stay at the API boundary. JSON deserialization stays in
the repository. Business rules live in service or domain.

## AI usage
This project was built with help from GitHub Copilot: GPT-6 Astra for initial planning and implementation, and Claude
Opus 5.5 for review and simplification.

The first step was to have Copilot explain the assignment to me, and then have it write an implementation plan that I
reviewed. In a conversation I explained my ideas, including frameworks and libraries to use. When I was happy with the
plan, I asked Copilot to implement it.

I validated AI output by reviewing each change, running the test suite, and manually exercising the HTTP endpoint.
That included checking the OpenAPI contract, repository loading rules, HTTP error handling, and nearest-store ordering
against the packaged application.

One concrete override: an earlier AI-assisted version added a configurable `limit` parameter and API-level warnings for
out-of-coverage searches. I removed that and simplified the implementation to always return the five nearest stores,
while keeping the coverage signal as a server-side log warning. That matches the assignment brief more closely and keeps
the public API smaller. I also pushed the JSON loading back to straightforward Jackson data binding after an earlier pass
had made it more complex than necessary.

## Design decisions
1. **Return exactly five stores.** The brief asks for the five closest stores, so `StoreService` enforces a fixed
   result count instead of exposing a configurable `limit` parameter. That keeps the API narrow and avoids discussing
   policy that the assignment did not ask for.
2. **Generated contract, handwritten application code.** The OpenAPI spec is the source of truth for the REST contract,
   but the controller, service, and repository stay handwritten. This keeps request/response shapes consistent without
   giving up readable application logic.
3. **Jackson binding over custom parsing.** The repository maps the seed file with Jackson and accepts its default
   string-to-double coercion for latitude and longitude, because the supplied JSON encodes coordinates as strings.
   Tests cover that behaviour explicitly, so the simpler loader is still deliberate and verified.
4. **Limited Lombok use.** Lombok is kept for small, local wins such as builders and constructor/logging boilerplate,
   while the domain and service logic remain plain Java records/classes.
5. **Separate test ownership by layer.** JUnit covers domain and repository rules, REST Assured covers real HTTP
   behaviour, and Cucumber covers business-level scenarios. In tests, `TestObjects` provides readable builders so most
   tests can avoid mocks and focus on behaviour.

## Known limitations / with more time

- The dataset is loaded once at startup and kept in memory; changing `stores.json` requires a restart.
- The nearest-store lookup is a full scan plus sort. For the current dataset size (587 stores) that is simple and more
  than adequate; for a much larger dataset I would investigate different algorithms.

## Approximate time spent

- Set up GIT and the initial project: 15 minutes
- Initial planning (including plan review/refinement): 1 hour
- Initial building: Free, just let Copilot do the work
- Review and rework: 2 hours, mainly simplifying things
- Final checks: 1 hour
