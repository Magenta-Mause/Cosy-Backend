# COSY Backend

Self-hosted game server management platform. Spring Boot backend managing Docker containers, auth, and REST API.

## Commands

```bash
./mvnw spring-boot:run           # Run dev server
./mvnw package                   # Build JAR
./mvnw test                      # Run tests
./mvnw spotless:apply            # Format code (Google Java Format, AOSP style)
./mvnw spotless:check            # Check formatting
```

## Code Style

- Spotless enforces Google Java Format (AOSP variant) — run `spotless:apply` before committing
- All JSON responses use snake_case naming (`spring.jackson.property-naming-strategy: SNAKE_CASE`)
- Lombok is used throughout — entities use `@Builder`, `@Getter`, `@Setter`

## Key Patterns

### API Response Wrapper
All responses are wrapped in `ApiResponse<T>` with `success`, `data`, `error`, `path`, `timestamp`, `statusCode`. The frontend unwraps this automatically.

### Authorization
Controller methods use `@NeedsValidation(Operation.XXX)` to declare required permissions. The `AuthorizationAspect` intercepts these, extracts `@ResourceId` parameters, and delegates to policy classes in `security/accessmanagement/policies/`.

### DTO Naming
- `*CreationDto` — POST request body
- `*UpdateDto` — PUT request body
- `*Dto` — response body (entities have `toDto()` methods with overloads for different visibility levels)

### Game Server Lifecycle
```
STOPPED -> AWAITING_UPDATE -> PULLING_IMAGE -> STARTING -> RUNNING
                                                  |
                                               FAILED -> STOPPED
```

### User Roles
- **OWNER**: Full system access, one per instance
- **ADMIN**: Manage servers/users, cannot manage other admins
- **QUOTA_USER**: Standard user with resource limits
