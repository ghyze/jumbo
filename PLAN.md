# Implementation plan

## Goal and scope

Implement a Java REST application that returns the closest Jumbo stores to a supplied latitude and longitude, using `src/main/resources/stores.json` as its classpath source data. Default to five results as required by the assignment, with a configurable result count.

Keep the solution small, explainable, and easy to run. Retain the existing Java 27, Spring Boot 4.1.1, Maven Wrapper, and Lombok setup. Use an OpenAPI-first REST contract, JUnit unit tests, REST Assured endpoint tests, and Cucumber integration scenarios. The assignment's recommended 4-6 hours is context, not a delivery estimate or implementation time budget; AI-assisted work can differ substantially from conventional estimates.

Explicitly exclude Docker, a graphical user interface (including Swagger UI), a real database, write endpoints, authentication, routing/travel-time services, and spatial indexing. IntelliJ HTTP requests are the manual testing interface.

This document is a proposal for review, not an implementation. Defaults below resolve details not specified by the assignment and can be revised before coding.

## 1. Contract and observable behavior

Define `src/main/resources/openapi/stores.yaml` as the source of truth for the API.

Proposed endpoint:

```http
GET /api/stores/nearest?latitude=52.0907&longitude=5.1214
GET /api/stores/nearest?latitude=52.0907&longitude=5.1214&limit=10
```

- Require both query parameters as numeric values: latitude in `[-90, 90]`, longitude in `[-180, 180]`, inclusive. Reject missing, blank, nonnumeric, non-finite, and out-of-range values with `400 Bad Request`.
- Configure `stores.search.max-results` at application level, defaulting to `5`. This single setting is both the default result count and the maximum any request can return. Bind it through validated Spring configuration properties; an explicitly invalid setting (blank, nonnumeric, fractional, nonpositive, or beyond the supported Java integer range) must fail startup clearly, not silently reset to five.
- Accept an optional `limit` query parameter. **Revised decision:** invalid supplied limits are now `400 Bad Request` instead of falling back with `INVALID_LIMIT_DEFAULTED`; treating invalid client input as an error is simpler to defend and removes the forgiving parser/warning path. Omitted or blank limit uses the configured count. A valid positive integer below the configured count reduces the result count; one above it is capped at the configured count.
- Return `200 OK` with a JSON object containing a `stores` array, ordered by increasing straight-line distance, and a `warnings` array. Return `min(effective limit, available stores)` results. Omission and ordinary capping need no warning. The warnings array is empty when the coverage warning does not apply.
- For mathematically valid coordinates outside the approximate Netherlands coverage box described below, still return nearest stores with a structured warning containing code `OUTSIDE_SUPPORTED_AREA` and a human-readable message explaining that the dataset covers the Netherlands and results may be far away. This is a non-blocking coverage warning, not a `400`, a server-log-only warning, or a guarantee about distance to a store.
- Each result exposes `id` (the seed's `uuid`), `addressName`, `city`, `postalCode`, `street`, `street2`, `street3`, numeric `latitude` and `longitude`, and numeric `distanceKm`. Preserve the seed's separate address components rather than guessing a combined house-number format. Normalize absent optional address components to empty strings and document this in the schema.
- Calculate distance using the Haversine formula with a documented mean Earth radius of 6,371.0088 km. This is geographic distance, not driving distance or travel time.
- Rank using unrounded distances, breaking exact ties by store ID for deterministic output. Return the computed distance without presentation rounding; clients may format it.
- Specify required response properties, global coordinate bounds, nonnegative distances, and the warnings schema. Describe `limit` as an optional positive integer query parameter. Let framework binding reject malformed input as `400`, while omitted or blank values use the runtime-configured default/cap (five out of the box). Include examples for omission, lower/capped counts, invalid-limit errors, geographic warnings, and errors.
- Use one documented `application/problem+json` error shape with `type`, `title`, `status`, `detail`, and `instance`. Return actionable validation messages without stack traces or internal implementation details. Unexpected failures produce `500` and are logged server-side.

Treat every entry in the supplied `stores` array as a candidate. Do not introduce an undocumented filter based on opening hours, collection-point status, or location type.

With `stores.search.max-results=5` and sufficient stores, omitted, invalid, `5`, or `10` limits all return five stores; `limit=3` returns three. With the setting changed to `10`, omission/invalid input returns ten, `limit=3` returns three, and `limit=20` returns ten. Fewer available stores always reduce the actual result count.

The cap bounds response size, not request frequency or the cost of the initial full distance scan. It is a useful abuse safeguard, but not comprehensive denial-of-service protection; rate limiting and infrastructure-level request controls remain outside this assignment.

### Verified seed geography and warning boundary

Inspection of the supplied file found 587 stores, all with Dutch-format postal codes. Their latitude spans `50.827243` to `53.442422`, and longitude spans `3.444601` to `7.107055`. All fall within an approximate European Netherlands bounding box; southern entries include Maastricht and Cadier en Keer. This supports treating this dataset as Netherlands-focused, without claiming a point-in-country validation or coverage of all present-day Jumbo stores.

Use the inclusive approximate box latitude `[50.7, 53.6]`, longitude `[3.2, 7.3]` for the warning policy. Warn if either coordinate lies outside its range. These rounded bounds contain the entire dataset with some margin, do not change implicitly with seed updates, and are a coverage heuristic rather than an administrative border. Some nearby Belgian/German locations or sea areas fall inside; that is an accepted simplification. A precise border polygon or distance-based coverage policy is not required.

Keep this policy separate from global coordinate validity: `(0, 0)` is valid but warns, whereas latitude `91` is invalid and returns `400`. Do not restrict the coordinate value object to the Netherlands. Document the box in the OpenAPI description and README, and test exact bounds plus values immediately inside/outside each edge.

## 2. Architecture and ownership

```text
HTTP request
  -> StoreController implements generated StoresApi
  -> StoreService
  -> StoreRepository
  -> JsonStoreRepository's immutable in-memory snapshot
  -> StoreService calculates, sorts, selects up to limit, and evaluates coverage
  -> StoreController maps results to generated response models
  -> HTTP response

Application startup -> JsonStoreRepository loads and validates stores.json once
```

Use packages underneath the `com.jumbo.stores` base package:

| Area | Responsibility |
| --- | --- |
| `api` | Handwritten REST controller, response mapping, and centralized exception handling |
| `api.generated` | Build-generated API interfaces and DTOs; no handwritten changes |
| `service` | Nearest-store orchestration and geographic distance calculation |
| `domain` | Immutable `Store`, shared `Coordinates` value object, and search results/warnings |
| `repository` | Read-only `StoreRepository` interface and JSON-backed implementation |

Keep generated models at the HTTP boundary. Neither service nor repository should depend on them. Keep JSON deserialization details inside the repository implementation. Use Lombok where useful for constructor injection and boilerplate; prefer immutable domain objects and avoid unnecessary layers or mapping libraries.

### Model mapping: handwritten initially

Prefer small, explicit mapping methods for this assignment: seed DTO to domain `Store`/`Coordinates` in the repository boundary, HTTP parameters to domain inputs in the controller, and domain search results to generated response DTOs in the API boundary. No additional request model is needed merely to wrap generated query parameters.

MapStruct is a reasonable alternative when repetitive field-to-field mappings grow: it generates type-safe code and can fail compilation for unmapped target properties. Here there are few mapping paths, while coordinate parsing, validation, and optional-address normalization need deliberate logic regardless of the mapper. Handwritten mapping keeps the build simpler alongside OpenAPI generation and Lombok. Test every exposed field with distinctive fixture values so swapped or omitted mappings are caught.

If adopted later, keep MapStruct interfaces at the boundaries, use Spring component integration and unmapped-target reporting set to `ERROR`, explicitly map differently named/nested fields, and keep business rules out of mappings. Verify annotation-processor ordering/compatibility with generated OpenAPI models and Lombok (including the Lombok-MapStruct binding where needed); never edit generated mapper implementations.

### Coordinates value object

- Introduce one immutable `Coordinates` class/record holding both `latitude` and `longitude`, validating finite values and global bounds on construction.
- Use it for store locations, search positions, coverage checks, and distance calculation. The distance helper accepts two `Coordinates` objects rather than four interchangeable numbers.
- Convert the seed's coordinate strings and REST query parameters into this type at their boundaries. Keep the HTTP contract's familiar separate latitude/longitude fields; an internal value object does not require changing the external JSON layout.

### Repository

- Provide a database-like read-only abstraction, initially `List<Store> findAll()`. Do not extend Spring Data interfaces or add unused CRUD methods: there is no database or write behavior.
- Inspect and explicitly map the actual seed format: an object containing `stores` and unrelated `attributes` metadata, not a bare array. The current dataset has 587 entries, unique UUIDs, and coordinates encoded as strings.
- Package the supplied JSON from the standard Maven resource location `src/main/resources/stores.json`. Keep the supplied file as the single maintained copy; do not depend on the launch working directory or create a second manually maintained copy.
- Load the complete resource synchronously during bean initialization, parse coordinate strings into numeric domain values, and publish one immutable snapshot before serving requests.
- Validate unique/nonblank IDs, required response fields, and finite/in-range coordinates. Ignore irrelevant seed fields and metadata, but do not silently drop malformed stores.
- Proposed startup policy: a missing/unreadable resource, malformed document, empty dataset, duplicate ID, or invalid store fails startup with a clear error identifying the issue. A malformed deployment should not look like a healthy service with incomplete results.
- Never read the file per request. Changes to the file require a restart. Return an immutable collection of immutable stores so concurrent requests cannot alter shared data.

### Service

- Accept a `Coordinates` object and a resolved positive result limit, enforce the configured maximum in the service as well, read the snapshot through the repository interface, calculate each distance once, sort by distance then ID, and take up to the effective count. Produce the coverage warning independently of the count.
- Put the Haversine calculation in a small independently testable class. Use radians consistently and clamp the intermediate Haversine value to `[0, 1]` to prevent floating-point errors near antipodal positions.
- Enforce coordinate validity through `Coordinates` construction and positive/capped limits for direct service calls too, rather than relying solely on generated HTTP validation. The service owns the configured default and cap; framework validation rejects invalid HTTP limits. Direct invalid domain arguments remain programming errors.
- Use a full scan and sort: `O(n log n)` time and `O(n)` temporary space are appropriate for 587 stores. Avoid a heap, geospatial library, or index unless requirements change.
- Naturally return zero to the requested number of results for small repository snapshots, even though the production loader rejects an empty seed file.

### Nice-to-have / future optimization

The initial full scan is deliberately simple, not the only viable approach. If the dataset or request volume grows, benchmark before changing it:

- A bounded max-heap can reduce ranking to `O(n log k)` with `O(k)` selection space for `k` requested results, but still calculates every distance.
- To avoid calculating all distances, build a suitable spatial index once at startup or later use a geospatial database repository. Use geographically correct candidate pruning (for example, a spherical nearest-neighbor index), followed by exact Haversine ranking. Preserve correctness near the antimeridian/poles and deterministic ID tie-breaking; naive latitude/longitude proximity or a fixed-radius prefilter can omit the actual nearest stores.
- Compare optimized results with the exhaustive baseline across varied coordinates and limits, and measure latency/memory before accepting added complexity. These are future improvements, not initial acceptance requirements.

### REST controller and errors

- Generate Spring API interfaces and request/response models; implement the generated interface in a handwritten `@RestController`. Generation defines the contract, not business logic or repository access.
- Keep the controller limited to input conversion, service delegation, and response/warning mapping.
- Pass an optional validated integer `limit` to the service. Omitted or blank limit uses the configured default; malformed, fractional, nonpositive, or out-of-range values are `400` client errors.
- Add Bean Validation support and ensure interface annotations are actually enforced by Spring MVC. Handle binding failures, missing parameters, validation failures, and domain coordinate errors consistently using Spring's exception-handling conventions.
- Log successful dataset loading with the store count, and log unexpected server failures. Avoid noisy per-store or per-request logging.
- Retain the existing Actuator dependency and minimal health endpoint; do not add a monitoring platform or expose sensitive management endpoints.

## 3. Build and dependency setup

1. Add and pin the OpenAPI Generator Maven plugin, generating during `generate-sources` into `target/generated-sources/openapi`. Configure Spring interfaces only, Jakarta imports, Bean Validation, and no generated application/controller stubs, tests, or UI.
2. Check the chosen generator's support for Spring Boot 4.1.1 and its serialization stack using a minimal generate-and-compile spike. Select a compatible generator version/configuration before implementing the full contract. Do not assume a Boot 3 preset is sufficient, downgrade the project silently, or patch generated Java.
3. Add only necessary dependencies: validation, generator-required annotations/runtime support, JUnit support if not already provided by the existing test starters, REST Assured, and Cucumber Java/Spring/JUnit Platform integration. Do not add Mockito explicitly unless a concrete test cannot reasonably use real objects or a small fake.
4. Use Spring Boot dependency management where available and pin unmanaged versions. Verify compatibility across Java 27, Boot's JUnit Platform version, REST Assured, and Cucumber rather than independently pinning conflicting JUnit versions.
5. Use Surefire for `*Test`/`*Tests` unit and endpoint tests, and Failsafe for the Cucumber `*IT` suite during `integration-test` and `verify`. Ensure the JUnit Platform suite engine discovers Cucumber scenarios and that they run exactly once.
6. Keep generated output and test reports out of version control. A clean wrapper build must regenerate the entire REST contract without IDE-specific setup.

## 4. Automated testing

Use small, deterministic fixtures with independently established expectations. Do not calculate expected rankings by invoking the production distance helper.

Add a test-only `TestObjects` factory exposing fresh builders with sensible, valid defaults, for example `TestObjects.store().id("store-b").coordinates(TestObjects.coordinates().latitude(52.1).build()).build()`. Provide builders for the domain objects tests actually need, not a general-purpose fixture framework. Make each test override the values relevant to its behavior and assign distinct IDs when creating multiple stores. Builders must not bypass domain validation or share mutable state.

Prefer real objects and the real JSON repository with small fixtures. Where service isolation genuinely helps, use a simple immutable in-memory implementation of `StoreRepository` populated with `TestObjects`, not a mocking framework. Use an observable/counting test resource to verify one-time loading. Reserve mocks for dependencies that cannot reasonably be exercised or replaced with a small fake, documenting why a mock is necessary. Keep JSON shape/error tests as explicit fixtures rather than generating their expected input through production mappings.

| Layer | Tools and setup | Coverage |
| --- | --- | --- |
| Unit | JUnit Jupiter with real objects and `TestObjects` builders | Identical points, known distances within tolerance, symmetry, antimeridian/polar/antipodal cases, finite results, coordinate value-object validation, coverage-box edges, default/capped limit handling, explicit model mappings |
| Configuration | Focused Spring context tests | Default maximum of five, override to ten, invalid configuration fails startup |
| Service unit | JUnit with real fixture-backed repository or small in-memory fake | Correct nearest IDs, configured maximum enforced even for direct calls, smaller counts, invalid domain limits, ascending distance, deterministic ties, small/empty results, coverage warnings, no mutation |
| Repository | JUnit with small JSON fixtures and an observable resource | Actual root structure, string coordinates, ignored metadata, immutable results, one-time reads, and all startup rejection cases |
| HTTP endpoint | REST Assured against `@SpringBootTest(webEnvironment = RANDOM_PORT)` | Actual HTTP status/content type/schema fields, numeric coordinates/distances, default five and configuration override to ten, smaller/capped counts, omitted and blank limits, invalid-limit 400s (text/fractional/zero/negative/out-of-range), ordering, missing/blank/malformed/non-finite/out-of-range coordinates, inclusive bounds, consistent problem responses |
| Integration acceptance | Cucumber + Spring Boot random-port server + REST Assured steps | Complete request-to-JSON-repository path, default nearest-five scenario, smaller requested count, application cap and invalid-limit errors, exact store location, tie ordering, fewer available stores, out-of-coverage warning with results, invalid coordinates |

For Cucumber, use a deterministic JSON fixture through the real repository loader, not a mocked service or repository. Configure test-only resource selection without adding a public runtime feature solely for tests. Keep scenarios focused on acceptance behavior; leave exhaustive mathematical and validation combinations to JUnit.

Include a separate application-context smoke test using the supplied production resource, confirming that it loads successfully and can serve five nearest stores. Verify packaged resource inclusion and executable-JAR startup as a final manual check.

Commands on Windows (PowerShell):

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Commands on macOS (Terminal):

```sh
./mvnw test
./mvnw verify
```

`test` runs unit and endpoint tests; `verify` also runs Cucumber. Check actual test/scenario counts so an undiscovered integration suite cannot be mistaken for success.

## 5. Manual testing and reviewer documentation

Add `http/stores.http` with IntelliJ HTTP Client requests and a local `baseUrl` variable. Include omitted/smaller/capped result counts, invalid limits returning the configured count with a warning, a search at a known store location, a missing coordinate parameter, nonnumeric coordinates, globally invalid coordinates, and valid coordinates outside the coverage box. Add simple client assertions for status, result count, ordering, and warnings where useful. Document the assumed application cap and how to rerun after overriding it. The same HTTP file should work on Windows and macOS.

Expand `README.md` with:

- JDK 27 prerequisites and Windows/macOS wrapper commands for generating/building, testing, and running; no globally installed Maven, database, or container runtime required. Document `JAVA_HOME` selection for the required JDK.
- Development startup via `.\mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS; packaged startup via `java -jar target\nearest-stores-0.0.1-SNAPSHOT.jar` on Windows or `java -jar target/nearest-stores-0.0.1-SNAPSHOT.jar` on macOS, plus copyable sample requests.
- How to run the IntelliJ HTTP file and both automated test stages.
- API/specification location, `stores.search.max-results` (default/cap five), and how to override it through application YAML or the Spring command-line argument `--stores.search.max-results=10`. Explain request capping and invalid-limit errors separately from invalid-coordinate errors. Document the coverage box and non-blocking warnings, distance units/formula, tie-breaking, startup loading, and resource packaging.
- Architecture and intentional trade-offs: in-memory read-only data, restart for updates, geographic rather than road distance, small-data full sorting, future optimization options, and exclusions.
- Actual approximate effort spent and a truthful account of AI assistance, how its output was validated, and any suggestions rejected or changed. Record actual experience, not invented estimates or disagreements.

### Windows/macOS portability

- Keep Java/build code platform-neutral: classpath resource streams, portable path APIs, no drive letters or shell-specific build steps, and explicit UTF-8 for text fixtures/resources.
- Preserve LF line endings and the executable Git file mode for `mvnw`; retain the Windows wrapper as well. Document `chmod +x mvnw` as a recovery step if executable permissions are lost during transfer.
- Ensure resource names match case exactly, tests do not assume Windows paths or a fixed port, and the packaged application can start outside the repository working directory.
- Run the wrapper verification, packaged-JAR startup, and the same HTTP requests on Windows. Use the available Mac for the equivalent checks before submission, including its locally installed JDK architecture/version. Record actual outcomes; do not claim macOS verification from Windows-only results.

## 6. Implementation sequence

1. **Contract and build foundation:** write the OpenAPI definition, configure generation, and prove generated interfaces compile on the existing stack. Confirm testing-library compatibility.
2. **Data and domain:** implement `Coordinates`, immutable models, `TestObjects` builders, JSON resource packaging, startup loading/validation, and repository tests.
3. **Nearest-store behavior:** implement Haversine distance, validated application default/cap, service ranking, coverage warnings, and focused unit tests without mocks where practical.
4. **REST boundary:** implement the generated interface, DTO/warning mapping, query defaults and validation/error handling, and real-HTTP REST Assured tests.
5. **Acceptance integration:** add the Cucumber runner, real JSON fixture, Spring context, reusable HTTP steps, and business scenarios.
6. **Reviewer experience:** add cross-platform IntelliJ requests and README instructions, run `.\mvnw.cmd test` and `.\mvnw.cmd verify`, then start the packaged JAR and execute the documented requests. Repeat the equivalent checks on the available Mac before submission.

Use this sequence as an ordering of work, not a time estimate. Do not attach speculative hour estimates to AI-agent tasks or inflate scope to fill the assignment's suggested effort. Track actual time for the README. If generation or test-stack compatibility causes difficulties, document them rather than removing requested test layers or expanding the architecture.

## Completion criteria

- A clean Maven Wrapper build generates the contract, compiles, and executes the intended test layers.
- The packaged application loads the supplied JSON once and requires no external service or checkout-relative file access.
- Results are deterministic and never exceed the configured application maximum (five by default) or available stores. Smaller valid requested limits are honored; omitted/blank limits use the configured count, while invalid supplied limits return 400. Invalid coordinates retain documented error responses; valid out-of-coverage coordinates return results plus a warning.
- Coordinates remain paired in a shared validated value object; tests use builders and real objects/small fakes in preference to mocks.
- The handwritten controller, service, and repository retain the defined boundaries.
- The README and IntelliJ requests let a reviewer build, start, and exercise the application on Windows or macOS without Docker or a UI, with actual cross-platform checks recorded.
- Generated Java is never edited by hand; no unrelated features or infrastructure are introduced.
