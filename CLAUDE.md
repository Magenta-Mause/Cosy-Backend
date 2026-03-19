# COSY Backend

## Project Overview

COSY (Cost Optimized Server Yard) is a self-hosted game server management platform. This is the Spring Boot backend that manages Docker containers, handles authentication, and provides the REST API.

### What COSY Does
- Manages game servers as Docker containers
- Provides real-time console, metrics, and file management
- Uses templates for quick server setup (Minecraft, ARK, Terraria, etc.)
- Supports user roles (Owner/Admin/User) with resource quotas
- Features Access Groups for fine-grained server permissions
- Offers public dashboards for community server status sharing
- Integrates webhooks (Discord, Slack, n8n) for notifications

### Architecture
- **Backend** (this repo): Spring Boot API
- **Frontend**: React SPA (see cosy-frontend repo)
- **Database**: PostgreSQL
- **Metrics**: InfluxDB (time-series data)
- **Logs**: Loki (log aggregation)
- **Proxy**: nginx routes `/api` to backend, `/` to frontend

## Tech Stack

- **Java**: 21
- **Framework**: Spring Boot 4.0
- **Build**: Maven
- **Database**: Spring Data JPA + PostgreSQL (H2 for tests)
- **Security**: Spring Security + JWT (jjwt)
- **Docker**: docker-java library
- **WebSocket**: Spring WebSocket (STOMP)
- **Metrics Storage**: InfluxDB client
- **RCON**: rcon-java for game server commands
- **API Docs**: SpringDoc OpenAPI
- **Code Style**: Spotless (Google Java Format, AOSP style)
- **Utilities**: Lombok

## Commands

```bash
./mvnw spring-boot:run           # Run dev server
./mvnw package                   # Build JAR
./mvnw test                      # Run tests
./mvnw spotless:apply            # Format code
./mvnw spotless:check            # Check formatting
```

---

## Project Structure

```
src/main/java/com/magentamause/cosybackend/
  annotations/           # Custom annotations
  configs/
    globalresponse/      # ApiResponse wrapper, exception handling
    properties/          # @ConfigurationProperties classes
    websockets/          # WebSocket configuration
    *.java               # CORS, InfluxDB, OpenAPI, etc.
  controllers/
    gameserver/          # Game server endpoints
      configurations/    # Access groups, webhooks, layouts, RCON, design
    *.java               # Auth, users, invites, templates, footer
  dtos/
    actiondtos/          # Request DTOs (creation, update)
    entitydtos/          # Response DTOs
    gamesapi/            # External game API DTOs
    loki/                # Loki log DTOs
    template/            # Template DTOs
    websockets/          # WebSocket message DTOs
  entities/
    gameserver/          # GameServerEntity + utility classes
    layout/              # Dashboard layout entities
    loki/                # Log message entity
    metric/              # Metric entity
    *.java               # User, Invite, Game, Template, Webhook entities
  exceptions/            # Custom exception classes
  repositories/          # Spring Data JPA repositories
  security/
    accessmanagement/    # Authorization aspect + policies
    config/              # Security configuration
    jwtfilter/           # JWT authentication filter
    websocket/           # WebSocket security
  services/
    auth/                # Authorization, JWT, security context
    core/
      games/             # Game entity service
      gameserver/        # Main game server logic
      logs/              # Log service (Loki integration)
      metrics/           # Metrics service (InfluxDB)
      templates/         # Template service
    engine/
      docker/            # Docker container management
    external/
      gamesapi/          # External game API client
      loki/              # Loki client
      templates/         # Template API client
    technical/           # JWT service, RCON service
    user/                # User + invite services
  websockets/            # WebSocket publishers
```

---

## API Response Wrapper

All API responses are wrapped in a standard format:

```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String error;
    private String path;
    private Instant timestamp;
    private int statusCode;
}
```

The frontend's axios instance unwraps this automatically, extracting `data` from the response.

---

## Authorization System

### @NeedsValidation Annotation
Controller methods use `@NeedsValidation(Operation.XXX)` to declare required permissions:

```java
@PostMapping("/{uuid}/start")
@NeedsValidation(Operation.GAME_SERVER_START_STOP)
public ResponseEntity<Void> startService(@PathVariable @ResourceId String uuid) {
    // ...
}
```

### Operations
Defined in `Operation.java` enum:
- **User**: `USER_GET_ALL`, `USER_UPDATE`, `USER_DELETE`, `USER_CHANGE_PASSWORD`, etc.
- **Game Server**: `GAME_SERVER_CREATE`, `GAME_SERVER_DELETE`, `GAME_SERVER_START_STOP`, `GAME_SERVER_UPDATE`, etc.
- **Configuration**: `GAME_SERVER_METRIC_CONFIG_CHANGE`, `GAME_SERVER_RCON_CONFIG_CHANGE`, etc.
- **Files**: `GAME_SERVER_FILES_READ`, `GAME_SERVER_FILES_UPDATE`
- **Webhooks**: `GAME_SERVER_WEBHOOK_CREATE`, `GAME_SERVER_WEBHOOK_READ`, etc.

### Policy Classes
Located in `security/accessmanagement/policies/`:
- `GameServerPolicy` - Game server CRUD authorization
- `GameServerConfigurationPolicy` - Config changes
- `GameServerFieldVisibilityPolicy` - DTO field filtering based on permissions
- `GameServerFilesPolicy` - File access
- `GameServerLogPolicy` - Log access
- `GameServerMetricPolicy` - Metrics access
- `UserPolicy` - User management
- `UserInvitePolicy` - Invite management
- `FooterPolicy` - Footer editing

### AuthorizationAspect
The `@Before` aspect intercepts `@NeedsValidation` methods, extracts `@ResourceId` parameters, and calls the appropriate policy validator.

---

## Docker Engine Integration

### EngineManager Interface
Abstraction for container engines. Currently only Docker is implemented.

### DockerEngineManager
Main orchestrator in `services/engine/docker/`:
- `start()` - Pull image, create container, inject env vars, start
- `stopAndRemove()` - Stop container, remove it (volumes preserved)
- `attachLogListener()` - Stream container logs via WebSocket
- `collectMetric()` - Collect CPU/memory/network stats
- `sendCommand()` - Send stdin command to container

### Supporting Classes
- `DockerImageManager` - Image pulling with progress callbacks
- `DockerLogStreamer` - Attach to container logs
- `DockerMetricsCollector` - Container stats collection
- `DockerCommandSender` - Send commands via stdin or RCON
- `DockerContainerFinder` - Find containers by server UUID
- `DockerEventHandler` - Listen for Docker events (start/stop/die)
- `DockerHostConfigFactory` - Build host config (ports, volumes, limits)
- `DockerVolumePathResolver` - Resolve volume mount paths

### Auto-Injected Environment Variables
Every container receives:
```
COSY_GAME_SERVER_UUID=<uuid>
COSY_GAME_SERVER_NAME=<server name>
COSY_GAME_SERVER_OWNER=<owner username>
COSY_CONTAINER_SECRET=<32-char secret, regenerated each start>
COSY_BASE_URL=<backend API URL>
COSY_METRICS_PERIOD_SECONDS=<polling interval>
```

---

## Game Server Service

`GameServerService` is the main service class:

### Lifecycle Methods
- `createGameServer()` - Create new server config
- `updateGameServerConfiguration()` - Update config (server must be stopped)
- `deleteGameServerById()` - Stop, remove container, delete volumes
- `startServer()` - Validate quotas, start async
- `stopServer()` - Stop container async
- `sendCommand()` - Send via RCON or stdin

### Status Management
On startup, `@PostConstruct` syncs status with actual Docker state and reattaches log listeners for running containers.

Status transitions trigger webhooks via `GameServerWebhookService`.

### Hardware Quota Checking
Before starting, `HardwareQuotaChecker` validates:
- User has sufficient remaining CPU/memory quota
- Server's limits don't exceed user's limits

---

## Entities

### GameServerEntity
Core entity with:
- Basic info: `uuid`, `serverName`, `owner`, `status`, `design`
- Docker config: `dockerImageName`, `dockerImageTag`, `dockerExecutionCommand`
- `portMappings`, `environmentVariables`, `volumeMounts`
- `dockerHardwareLimits` (CPU/memory)
- `rconConfiguration`
- `accessGroups`, `webhooks`, `publicDashboard`
- `metricLayout`, `privateDashboardLayouts`
- `customMetricHolder` (JSON map for custom metrics)

Has multiple `toDto()` methods for different visibility levels based on user permissions.

### UserEntity
- `uuid`, `username`, `password`
- `role` (OWNER, ADMIN, QUOTA_USER)
- `dockerHardwareLimits` (user-level quotas)

### Other Entities
- `GameEntity` - Game metadata (fetched from external API)
- `TemplateEntity` - Cached templates
- `WebhookEntity` - Webhook configurations
- `UserInviteEntity` - Pending invitations
- `FooterEntity` - Footer contact info

---

## DTOs

### Naming Convention
- `*CreationDto` - Request body for POST (create)
- `*UpdateDto` - Request body for PUT (update)
- `*Dto` - Response body (entity → DTO)

### Entity-to-DTO Pattern
Entities have `toDto()` methods, often with overloads:
```java
public GameServerDto toDto() { ... }                    // Full DTO
public GameServerDto toPublicDto() { ... }              // Public fields only
public GameServerDto toDto(UserEntity user) { ... }     // Permission-filtered
public GameServerDto toDto(List<Permission> perms) { }  // Explicit permissions
```

---

## WebSocket Publishers

Real-time updates via STOMP WebSocket:

| Publisher | Topic | Purpose |
|-----------|-------|---------|
| `GameServerUpdatePublisher` | `/topic/game-server/{uuid}` | Server status/config changes |
| `GameServerLogWebsocketPublisher` | `/topic/game-server/{uuid}/logs` | Live log streaming |
| `GameServerMetricsPublisher` | `/topic/game-server/{uuid}/metrics` | Live metrics |
| `GameServerDockerProgressPublisher` | `/topic/game-server/{uuid}/docker-progress` | Image pull progress |
| `UserPermissionsPublisher` | `/topic/user/{uuid}/permissions` | Permission changes |

---

## External Services

### Games API
External service at `cosy-game-api.jannekeipert.de` providing game metadata (name, cover images).

### Templates API
External service at `cosy-templates.jannekeipert.de` providing pre-configured server templates.

### Loki
Log aggregation. Logs are pushed to Loki and queried for historical display.

### InfluxDB
Time-series metrics storage. Container stats are written periodically and queried for charts.

---

## Configuration Properties

Located in `configs/properties/`:

| Class | Prefix | Purpose |
|-------|--------|---------|
| `CorsProperties` | `cosy.cors` | Allowed origins |
| `EngineProperties` | `cosy.engine.docker` | Docker socket, volume paths |
| `InfluxProperties` | `cosy.influx` | InfluxDB connection |
| `LokiProperties` | `cosy.loki` | Loki connection |
| `GamesApiProperties` | `cosy.games-api` | External games API URL |
| `CosyTemplateApiProperties` | `cosy.templates-api` | Templates API URL |
| `FooterProperties` | `cosy.footer` | Default footer values |
| `DefaultProperties` | `cosy.defaults` | Default admin credentials, dummy data flag |

---

## Key Concepts

### Game Server Lifecycle
```
STOPPED → AWAITING_UPDATE → PULLING_IMAGE → STARTING → RUNNING
                                                ↓
                                             FAILED
                                                ↓
                                             STOPPED
```

### User Roles
- **OWNER**: Full system access, one per instance
- **ADMIN**: Manage servers/users, cannot manage other admins
- **QUOTA_USER**: Standard user with resource limits

### Access Groups
Per-server permission sets. Permissions from `GameServerAccessPermission` enum include:
`SEE_SERVER`, `START_STOP_SERVER`, `READ_SERVER_LOGS`, `SEND_COMMANDS`, `CHANGE_SERVER_FILES`, `CHANGE_SERVER_CONFIGS`, `ADMIN`, etc.

### Custom Metrics API
Game servers POST to:
```
PUT /api/internal/game-server/custom-metric/{uuid}
Authorization: <COSY_CONTAINER_SECRET>
Content-Type: application/json
{"player_count": 12, "tps": 19.8}
```

---

## Security Notes

- Requires Docker socket access (`/var/run/docker.sock`) - root-equivalent privileges
- JWT-based authentication for users (1h access token, 1 month refresh)
- Container secret authentication for internal APIs (regenerated each start)
- RCON passwords stored for supported games
- All responses use snake_case JSON naming
