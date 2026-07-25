# Spring Boot Rules

## Layered Architecture

**DO:**
- Keep controllers thin: validate input, delegate to service, map the result to a DTO, return response.
- Put all business logic in the service layer.
- Put all data access in the repository layer.
- Map entities to their response DTO with an `entity.toDto()` method on the entity — this is the mapping convention used throughout the codebase (`UserEntity`, `GameServerEntity`, `FooterEntity`, … all expose `toDto()`; there is no MapStruct/ModelMapper). Keep other business logic out of entities.
- Define a controller interface (the API "contract") that holds all REST-specific annotations: `@RequestMapping`, `@Operation`, `@ApiResponse`/`@ApiResponses`, `@Tag`, `@Parameter`, etc. The interface lives in `controllers/api` and is named `*Api`. The `*Controller` class in `controllers/impl` implements it and contains only the method bodies. This is the real split used across this codebase (`UserEntityApi`/`UserEntityController`, `GamesApi`/`GamesController`, …).

  ```java
  // controllers/api/UserEntityApi.java — the contract interface
  @Tag(name = "User Entity", description = "User management")
  @RequestMapping("/user-entity")
  public interface UserEntityApi {

      @Operation(summary = "Get user by UUID")
      @ApiResponses({
          @ApiResponse(responseCode = "200", description = "User found"),
          @ApiResponse(responseCode = "404", description = "User not found")
      })
      @GetMapping("/{uuid}")
      ResponseEntity<UserEntityDto> getUserEntity(
              @Parameter(description = "User UUID") @PathVariable String uuid);
  }

  // controllers/impl/UserEntityController.java — the implementation
  @RestController
  @RequiredArgsConstructor
  @Slf4j
  public class UserEntityController implements UserEntityApi {

      private final UserEntityService userEntityService;

      @Override
      @NeedsValidation(Operation.USER_GET_BY_UUID) // access-management aspect, see AUTH.md
      public ResponseEntity<UserEntityDto> getUserEntity(@ResourceId String uuid) {
          return ResponseEntity.ok(userEntityService.getUserByUuid(uuid).toDto());
      }
  }
  ```

  This keeps the controller class readable, separates the API contract from the implementation, and makes the OpenAPI annotations easy to find and maintain. Note the responses are wrapped in a global `ApiResponse<T>` envelope by `GlobalResponseWrapper` — see Error Handling below.

**DON'T:**
- Call repositories directly from controllers.
- Serialize a JPA entity directly in a REST response — a controller returning an entity from a service must map it to a `*Dto` (via `entity.toDto()`) first.
- Put `@Operation`, `@ApiResponse`, or other OpenAPI annotations directly on the `*Controller` class — put them on the `*Api` interface instead.

---

## Package Structure

Organise the codebase **by component type, not by feature**. All controllers live together, all entities live together, and so on. A single feature is therefore spread across the type packages rather than being confined to one feature package. This is the actual layout of `com.magentamause.cosybackend`:

```
com.magentamause.cosybackend
├── CosyBackendApplication        # @SpringBootApplication (root — drives component scan)
├── controllers/
│   ├── api/                      # *Api interfaces — REST/OpenAPI annotations only
│   ├── impl/                     # *Controller classes — implement the *Api, method bodies only
│   └── gameserver/{api,impl}/    # same api/impl split, grouped for the game-server area
├── dtos/
│   ├── actiondtos/               # request DTOs (input), sub-packaged (e.g. user/, gameserver/)
│   ├── entitydtos/               # response DTOs mapped from entities (*Dto)
│   └── gamesapi/ template/ loki/ websockets/   # DTOs for specific integrations/channels
├── entities/                     # JPA @Entity classes (sub-packaged: gameserver/, layout/, loki/, metric/)
├── repositories/                 # Spring Data repositories
├── services/                     # business logic, sub-packaged by domain (auth/, core/, engine/, external/, user/, technical/)
├── security/                     # SecurityFilterChain, JWT filter, access-management aspect + policies
├── exceptions/                   # custom RuntimeExceptions (sub-packaged: docker/, gameapi/)
├── configs/                      # @Configuration; configs/properties/ holds @ConfigurationProperties;
│   └── globalresponse/           # GlobalExceptionHandler + GlobalResponseWrapper + ApiResponse envelope
├── annotations/                  # custom annotations (+ their validators)
├── websockets/                   # STOMP/WebSocket endpoints
└── util/                         # shared helpers
```

**DO:**
- Place a new class in the package for its **type** (controller, entity, repository, service, …), not its feature.
- Split every endpoint into `controllers/api` (the `*Api` interface) and `controllers/impl` (the `*Controller`), per Layered Architecture above.
- Separate DTOs by direction: request/input DTOs in `dtos/actiondtos`, entity-mapped response DTOs in `dtos/entitydtos`; keep integration-specific DTOs (`gamesapi`, `template`, `loki`, `websockets`) in their own sub-packages.
- Keep `@SpringBootApplication` (`CosyBackendApplication`) at the root package so component scanning covers every type package automatically.
- Sub-package `services/` by domain (e.g. `auth`, `core`, `engine`, `external`, `user`) as it grows, rather than creating a package per feature.

**DON'T:**
- Introduce feature packages that mix controllers, services, entities, and DTOs together.
- Put request and response DTOs in the same package — keep the `actiondtos` / `entitydtos` split.
- Serialize entities (from `entities/`) directly in responses — always map to a DTO with `toDto()`.

---

## Transaction Management

**DO:**
- Annotate every service method that writes to the database with `@Transactional`.
- Use `@Transactional(readOnly = true)` on read-only service methods to allow DB-level optimisations.
- Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` (with an explicit `@Query`) when you need to
  serialize access to a row, and give the method a `...Locked` suffix — `UserInviteRepository.findBySecretKeyLocked`
  is the existing example (invite redemption must not race).

**DON'T:**
- Leave multi-step write operations without `@Transactional` — partial commits cause data integrity bugs.
- Call `@Transactional` methods from within the same class (self-invocation bypasses the proxy).
- Use `REQUIRES_NEW` without understanding it suspends the outer transaction.

---

## Database & JPA

The schema is **Flyway-managed**; Hibernate runs with `ddl-auto: validate`, so it will
never create or alter a table for you — a missing migration means the app fails to boot.

**DO:**
- Ship every schema change as a new `V<N>__*.sql` in `src/main/resources/db/migration/`,
  in the same commit as the entity change. Migrations to date: `V1__baseline.sql`,
  `V2__widen_template_description.sql`, `V3__drop_orphaned_legacy_tables.sql`.
- Name pessimistic-lock query methods with a `...Locked` suffix — `UserInviteRepository`
  has the one example, `findBySecretKeyLocked` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`
  on an explicit `@Query`).
- Use `Instant` for all timestamps (`java.time.Instant`) — it is used throughout the entities.

**DON'T:**
- Edit a migration that has already been applied — Flyway checksums it and will fail on
  the next start. Add a new versioned migration instead.
- Use `new java.util.Date()` — use `Instant.now()`.
- Reach for `ddl-auto: update` to "fix" a validation failure — that hides the missing
  migration and desynchronises deployed instances.

---

## Configuration Properties

**DO:**
- Use `@ConfigurationProperties(prefix = "...")` with a validated, nested POJO for all app-specific config.
- Annotate config classes with `@Validated` and use Bean Validation (`@NotNull`, `@Min`, etc.) on fields.
- Bind config once at startup — fail fast if required properties are missing.

**DON'T:**
- Use `@Value` for anything beyond trivial single values — it does not compose or validate.
- Scatter config reads across multiple classes without a central properties class.

---

## Error Handling & the Response Envelope

This codebase does **not** use `ProblemDetail`/RFC 7807. Every HTTP response — success or
error — is wrapped in a custom `ApiResponse<T>` envelope. `GlobalResponseWrapper` (a
`ResponseBodyAdvice` in `configs/globalresponse`) wraps successful responses, and
`GlobalExceptionHandler` (`@RestControllerAdvice` in the same package) produces the same
envelope for errors.

```java
// configs/globalresponse/ApiResponse.java — the response/error envelope (snake_case JSON)
@Data @Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;
    private String path;
    @Builder.Default private Instant timestamp = Instant.now();
    private int statusCode;   // serialized as status_code
}
```

**How errors are signalled — two real patterns:**

- **Throw `org.springframework.web.server.ResponseStatusException`** with the status and a
  message. This is the common case (used throughout the services, e.g.
  `throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")`).
  `GlobalExceptionHandler` has a dedicated `@ExceptionHandler(ResponseStatusException.class)`
  that maps it into the `ApiResponse` envelope.
- **A custom `RuntimeException` in `exceptions/`**, either annotated with `@ResponseStatus(...)`
  to set the HTTP status (e.g. `HardwareLimitException` → `403`), or given its own
  `@ExceptionHandler` in `GlobalExceptionHandler` (e.g. `GamesApiError` → `502`).

**DO:**
- Prefer throwing `ResponseStatusException` with the right `HttpStatus` for expected
  failure cases; reach for a named exception in `exceptions/` when the failure is a
  reusable domain concept or needs custom handling.
- Name exceptions after the domain concept and failure reason: `HardwareLimitException`,
  `ServerAlreadyStoppedException`, `NoAuthenticationFoundException`, etc.
- Let `GlobalExceptionHandler` own the mapping to `ApiResponse` — including the catch-all
  `@ExceptionHandler(Exception.class)` that returns a generic `500` (it deliberately
  re-throws for `/api/v3/api-docs` and `/api/swagger-ui` paths so springdoc still works).
- Keep validation/framework failures (`MethodArgumentNotValidException`,
  `HttpMessageNotReadableException`, `MissingRequestCookieException`, …) mapped in the
  handler, not in controllers.

**DON'T:**
- Introduce `ProblemDetail`/`application/problem+json` — it would break the uniform
  `ApiResponse` envelope the frontend expects.
- Return raw JPA entities, stack traces, or internal messages in the `data`/`error` fields
  sent to clients.
- Catch and swallow exceptions without logging or rethrowing.
- Bypass the envelope by hand-building error response bodies in a controller.

> **Possible future improvement (not the current state):** a shared `BaseException`
> carrying its own `HttpStatus`, handled by a single `@ExceptionHandler(BaseException.class)`,
> would remove the per-type handler methods. It does **not** exist today — do not document
> it as if it does.

---

## Logging

**DO:**
- Log at `DEBUG` for the happy path (successful operations, normal flow).
- Log at `WARN` for recoverable client errors (4xx) — the client did something wrong, not the service.
- Log at `ERROR` for server errors (5xx) — something the service is responsible for.
- Include enough context in log messages to diagnose the issue without a debugger (IDs, statuses, key values).
- Use SLF4J with parameterised messages: `log.debug("Processing item {}", id)` — never string concatenation.

**DON'T:**
- Log at `WARN` or `ERROR` for expected 4xx responses — this creates noise in alerting.
- Log sensitive data (PII, tokens, passwords).
- Use `System.out.println` or `printStackTrace()`.

---

## Validation

**DO:**
- Validate all incoming request bodies and path/query parameters with Bean Validation (`@Valid`, `@NotNull`, `@Size`, etc.) at the controller layer.
- Validate configuration properties at startup with `@Validated`.
- Use custom `ConstraintValidator` implementations for domain-specific rules.

**DON'T:**
- Duplicate validation logic across the controller and service layer.
- Perform validation inside entity setters — validate at the boundary.

---

## Security

**DO:**
- Define coarse route rules in the single `SecurityFilterChain` bean (`SecurityConfiguration`).
- For fine-grained, resource-aware access control, use the codebase's custom access-management aspect: annotate controller methods with `@NeedsValidation(Operation.X)` and mark the resource argument with `@ResourceId`. `AuthorizationAspect` resolves the resource and runs the matching policy in `security/accessmanagement/policies`. (Method-level `@PreAuthorize` is **not** used here.)
- Validate and parse JWTs in a dedicated filter/component (`JwtFilter`, `JwtUtils`) — not inside business logic.
- Externalize security config (JWT secret/expirations, CORS origins) via `@ConfigurationProperties` (`JwtProperties`, `CorsProperties`).

**DON'T:**
- Hardcode roles, issuers, or secrets in source code.
- Disable CSRF without explicitly documenting why (e.g. stateless JWT API).
- Mix authentication logic with business logic.

---

## Testing Conventions

**Be aware of the starting point:** this repo currently has very little test coverage —
three classes in `src/test`, namely the `CosyBackendApplicationTests` context load and the
two Flyway tests. There is no established service-unit-test suite yet, so treat the notes
below as the direction to build in rather than a pattern to copy from many examples.

**DO:**
- Use JUnit 5. Spring Boot's `spring-boot-starter-test` is on the classpath, so JUnit 5,
  Mockito and AssertJ are all available without adding dependencies.
- Gate anything that needs a real Postgres behind
  `@EnabledIfEnvironmentVariable(named = "CI_PG_TESTS", matches = "true")`, as
  `FlywayMigrationTest` and `FlywayUpgradePathTest` do. CI sets that variable and provides
  a `postgres:16-alpine` service; a plain local `./mvnw verify` skips those tests instead
  of failing on a missing database.
- Add a migration test alongside a migration that does anything non-trivial —
  `FlywayUpgradePathTest` exists to prove an existing database upgrades cleanly, not just
  that a fresh one builds.
- Prefer plain unit tests with mocks for service logic; `@SpringBootTest` boots the whole
  context and is slow.

**DON'T:**
- Assert only that no exception was thrown — assert the actual result.
- Write a test that silently needs a local Postgres without the `CI_PG_TESTS` guard — it
  will fail for every contributor who just runs `./mvnw verify`.
- Cover only the happy path and skip error branches.

---

## Code Style

**Formatting is enforced by Spotless — run it before you push.** CI's first step is
`mvn -B spotless:check` (google-java-format 1.17.0, AOSP style, UNIX line endings), so an
unformatted file fails the build before any test runs:

```
./mvnw spotless:apply    # format
./mvnw spotless:check    # what CI runs
```

Two things about this that catch people out:

- **`./mvnw verify` does not run Spotless.** The plugin is declared in `pom.xml` with no
  `<executions>` block, so it is bound to no lifecycle phase — it only runs when invoked
  as a standalone goal. A clean local `verify` therefore tells you nothing about the
  formatting gate; run `spotless:apply` separately or CI will go red on a green local build.
- **Run it on JDK 21** (Temurin 21 is what CI uses). On JDK 25, spotless-maven-plugin
  2.43.0 + google-java-format fails outright with:

  ```
  NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics()
  ```

  That is a toolchain mismatch, not a problem with your code — `spotless:apply` aborts
  without formatting anything. Switch to JDK 21 and re-run.

**DO:**
- Use explicit imports — no wildcard imports (`import java.util.*`).
- Use Lombok (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`) to reduce boilerplate, as the codebase does throughout, but prefer explicit constructors when clarity matters.

**DON'T:**
- Hand-format around Spotless — if the formatter disagrees with you, let it win.
- Suppress warnings (`@SuppressWarnings`) without a comment explaining why.
- Mix concerns in a single class (e.g. a service that also handles HTTP response formatting).
