# 🏡 Cosy — Backend

> The core orchestration engine for **Cosy** (Cost Optimised Server Yard) — a self-hostable platform for hosting and managing game servers.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build](https://github.com/Magenta-Mause/Cosy-Backend/actions/workflows/check.yml/badge.svg)](https://github.com/Magenta-Mause/Cosy-Backend/actions/workflows/check.yml)

This Spring Boot application acts as the "Town Hall" of the Cosy ecosystem. It manages game server containers, handles
file I/O operations directly on the host, and coordinates with specialized data stores for metrics, logs, and backups.

---

## 📖 Overview

### What problem does this solve?

Self-hosting game servers usually means juggling raw Docker commands, scattered log files, ad-hoc metrics, and manual
backups. **Cosy** turns a single host into a managed game-server yard: it provisions containers from reusable
templates, streams live logs and metrics, enforces access control, and exposes everything through a clean REST + WebSocket API.

This repository is the **backend** — the orchestration and API layer. It is one part of the wider Cosy platform:

| Repository | Role |
| --- | --- |
| [Cosy](https://github.com/Magenta-Mause/Cosy) | Main project / self-host download & install repo |
| [Cosy-Frontend](https://github.com/Magenta-Mause/Cosy-Frontend) | Web UI |
| **Cosy-Backend** (this repo) | Orchestration engine & REST/WebSocket API |
| [Cosy-Docs](https://github.com/Magenta-Mause/Cosy-Docs) | Documentation ([cosy-hosting.net](https://cosy-hosting.net)) |

### Key features

- 🎮 **Game server orchestration** — create, start, stop and manage containerized game servers via a pluggable runtime strategy (Docker).
- 🧩 **Template-driven provisioning** — spin up servers from templates served by the Cosy template & game services.
- 📈 **Metrics** — system metrics into InfluxDB, plus **custom, game-specific metrics** pushed by the game server itself (see below).
- 📜 **Log streaming** — server logs shipped to Grafana Loki and streamed to clients over WebSockets.
- 🔐 **Security** — JWT-based authentication and a policy-based access-management layer.
- 🗂️ **Direct host file I/O** — secure file management for server data using a native library (`cosyfs`), with a portable fallback.
- 🔌 **RCON support** — interact with running game servers over RCON.

## 🛠️ Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4.0 (Web MVC + WebFlux + WebSocket, Spring Security, Spring Data JPA, Actuator)
- **Build tool:** Maven (via the bundled wrapper)
- **API docs:** springdoc-openapi (Swagger UI)
- **Datastores:** PostgreSQL (application data), InfluxDB (metrics), Grafana Loki (logs)
- **Native lib:** `cosyfs` — a Rust `cdylib` for secure file operations on Linux
- **Runtime:** Docker (game server management via the Docker socket)

---

## 🚀 Getting Started

### Prerequisites

- **JDK 21** (e.g. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)) — required.
- **Docker Engine** with the **Docker Compose v2** plugin (`docker compose ...`) — required for the dev infrastructure and as the game server runtime.
- **Maven** — optional; the repo ships a wrapper (`./mvnw`), so a separate install is not needed. The CI/Docker builds use Maven 3.9.x.
- **Rust 1.93+** — only needed if you build the container image or the native `cosyfs` library yourself. The multi-stage `Dockerfile` builds it for you.

> The backend listens on **port 8080** with a context path of **`/api`**. The dev infrastructure uses ports **5432** (Postgres), **3100** (Loki via nginx), and **8086** (InfluxDB).

### Installation

```bash
# 1. Clone
git clone https://github.com/Magenta-Mause/Cosy-Backend.git
cd Cosy-Backend

# 2. Start the "Data Silos" (Postgres, InfluxDB, Loki + nginx auth proxy)
cd infrastructure
docker compose up -d
cd ..

# 3. (Optional) create your local env file
cp .env.example .env   # then edit values
```

### Configuration

Configuration lives in `src/main/resources/application.yaml`. The defaults are wired for **local development** and match the
`infrastructure/docker-compose.yaml` services out of the box, so you can run without any extra setup.

For deployments, override values via environment variables (Spring Boot relaxed binding) or a local profile. The following
environment variables are explicitly supported (see [`.env.example`](./.env.example)):

| Variable | Purpose | Dev default |
| --- | --- | --- |
| `COSY_JWT_SECRET_KEY` | JWT signing key — **change in production** | (insecure sample key) |
| `COSY_LOKI_USER` | Loki basic-auth user | `loki-user` |
| `COSY_LOKI_PASSWORD` | Loki basic-auth password | `loki-password` |
| `COSY_INFLUX_TOKEN` | InfluxDB API token | `cosy-admin-token` |
| `COSY_DOCKER_SOCKET_PATH` | Docker socket URI | `unix:///var/run/docker.sock` |
| `COSY_DOCKER_VOLUME_DIRECTORY` | Host dir for game server bind mounts | `./dummy/cosy/volume-mounts` |
| `COSY_DOCKER_BACKEND_VOLUME_MOUNT_PATH` | Backend-side path to those mounts | `./dummy/cosy/volume-mounts` |
| `COSY_DOCKER_CONTAINER_PREFIX` | Prefix for managed container names | `cosy-` |
| `COSY_FOOTER_FULL_NAME` / `_EMAIL` / `_PHONE` / `_STREET` / `_CITY` | Imprint / footer contact info | sample values |

Other settings (CORS origins, external service URLs, JWT token lifetimes, InfluxDB org/bucket, etc.) are configured directly
in `application.yaml`. To point the Docker runtime at a Windows named pipe, copy the provided
`src/main/resources/application-local.template.yaml` to `application-local.yaml` (git-ignored) and adjust.

> ⚠️ The bundled `COSY_JWT_SECRET_KEY` default and the `infrastructure/htpasswd` credentials are for **local development only**.
> Replace them before deploying (see [Dependencies](#-dependencies) for regenerating the Loki `htpasswd`).

### Quick Start

With the infrastructure running (see [Installation](#installation)), start the backend with the Maven wrapper:

```bash
./mvnw spring-boot:run
```

Once it boots, the API is available at **<http://localhost:8080/api>** and the interactive API explorer at
**<http://localhost:8080/api/swagger-ui/index.html>**. On first run with `initialize-dummy-data` enabled, a default owner
account (`admin` / `admin`) and sample data are created — **change these before exposing the instance**.

Prefer containers? Build and run the full image (this also compiles the native `cosyfs` library):

```bash
docker build -t cosy-backend .
docker run --rm -p 8080:8080 cosy-backend
```

---

## 🧑‍💻 Development

### Project structure

```
Cosy-Backend/
├── src/main/java/com/magentamause/cosybackend/
│   ├── controllers/     # REST + game-server API controllers
│   ├── services/        # Core logic: core, engine (docker), external, auth, user, technical
│   ├── entities/        # JPA entities (game servers, users, metrics, loki, layout)
│   ├── repositories/    # Spring Data JPA repositories
│   ├── dtos/            # Request/response & entity DTOs
│   ├── security/        # JWT filter, access-management policies, websocket security
│   ├── websockets/      # WebSocket publishers (logs, metrics, progress, updates)
│   ├── configs/         # Spring configuration (OpenAPI, CORS, Influx, native FS, engine, ...)
│   └── annotations/     # Custom validation annotations
├── src/main/resources/  # application.yaml + profiles, native libs
├── cosyfs/              # Rust native library (cdylib) for secure Linux file I/O
├── infrastructure/      # docker-compose dev stack (Postgres, Influx, Loki, nginx)
├── scripts/             # Helper scripts (e.g. remove all containers)
├── Dockerfile           # Multi-stage build (cosyfs → jar → runtime)
└── pom.xml
```

### Available commands

All commands use the Maven wrapper (`./mvnw` on Linux/macOS, `mvnw.cmd` on Windows).

| Command | Description |
| --- | --- |
| `./mvnw spring-boot:run` | Run the application locally |
| `./mvnw clean package` | Build the executable jar |
| `./mvnw test` | Run unit & integration tests |
| `./mvnw verify` | Full build + tests (what CI runs) |
| `./mvnw spotless:check` | Verify code formatting |
| `./mvnw spotless:apply` | Auto-format the code |
| `docker compose up -d` | Start dev infrastructure (run in `infrastructure/`) |
| `docker compose down -v` | Stop infrastructure and wipe volumes (reset state) |
| `docker build -t cosy-backend .` | Build the container image |

### Development workflow

1. Start the infrastructure (`cd infrastructure && docker compose up -d`).
2. Make your changes and run the app with `./mvnw spring-boot:run`.
3. Test locally with `./mvnw verify`.
4. **Format before pushing:** run `./mvnw spotless:apply`. CI runs `spotless:check` and will fail on unformatted code.
5. Open a pull request against `main`. The `check.yml` workflow runs Spotless and `mvn verify` on every PR.

#### Code style & linting

We maintain a strict code style using **Spotless** with **Google Java Format (AOSP style)**.

```bash
./mvnw spotless:check   # verify only
./mvnw spotless:apply   # auto-fix
```

### 🧪 Testing

```bash
./mvnw test
```

Tests use the `test` profile with an in-memory H2 database (PostgreSQL compatibility mode), so no running infrastructure is required.

---

## 📄 API Documentation

The backend exposes an OpenAPI 3 spec via **springdoc-openapi**. With the app running:

- **Swagger UI:** <http://localhost:8080/api/swagger-ui/index.html>

The API ("Cosy API") uses **Bearer / JWT** authentication. Actuator health endpoints (`health`, `readiness`, `liveness`) are
exposed under `/api/actuator`.

### Custom Metrics (Game Server → Cosy Backend)

Cosy supports **custom, game-specific metrics** published directly by your game server (for example via a Minecraft mod/plugin). This is useful for values that Cosy cannot collect automatically, such as:

- `playerCount`
- `tps`
- `mspt`
- current game state / map name
- modpack-specific stats

The server publishes a JSON object (a simple key/value map). Cosy stores this as the server's *current custom metrics* and will use it for display/streaming.

#### 1) Required environment variables

Your game server process/container must have these environment variables set (your mod/plugin reads them at runtime):

- `COSY_BACKEND_URL` — Base URL of the Cosy backend (e.g. `https://<your-domain>`)
- `COSY_GAMESERVER_UUID` — The UUID of this game server in Cosy
- `COSY_CONTAINER_SECRET` — Secret used to authenticate custom metric updates

> Tip: Read these once at server startup and fail fast (log a clear error) if any are missing.

#### 2) Validate credentials (GET)

Before publishing metrics, verify that your credentials are correct:

```
GET /api/internal/game-server/test-connection/{game-server-uuid}
Authorization: {secret}
```

Response `2xx` with body:

```json
{
  "data": false,
  "error": null,
  "path": "/api/internal/game-server/test-connection/f98585aa-78d0-4fdf-9b3b-2f4b8c66d6e0",
  "status_code": 200,
  "success": true,
  "timestamp": "2026-02-18T20:29:54.046171033Z"
}
```

If validation fails (non-2xx), do not spam updates — log the error and retry with backoff.

#### 3) Publish custom metrics (PUT)

```
PUT /api/internal/game-server/custom-metric/{game-server-uuid}
Authorization: {secret}
Content-Type: application/json
```

Body — a flat JSON key/value map:

```json
{
  "playerCount": 12,
  "tps": 19.8,
  "mspt": 5.3,
  "motd": "Vanilla+ SMP",
  "pvpEnabled": true
}
```

Cosy treats this payload as the server's **current custom metric holder**. Publish on an interval (e.g. every 5–10 seconds) and/or when values change.

**Recommendations**

- Keep metric keys stable (e.g. always `playerCount`, not sometimes `players`).
- Use primitive values: **number**, **string**, **boolean**.

---

## 🛜 Dependencies

Cosy uses a **Postgres** instance for data storage and **Loki** for server logs.
The Postgres setup is straightforward and can be found in the `infrastructure` folder.
The Loki setup is a bit more involved as Loki itself doesn't have any authorization mechanism, so we use an nginx
reverse proxy in front of it to add basic auth. For this, the repository ships an `infrastructure/htpasswd` file with a
development username/password (`loki-user` / `loki-password`).

**This must be changed in production.** Generate a hashed `htpasswd` file with:

```bash
# bash
docker run --rm httpd:2.4-alpine htpasswd -nbB loki-user loki-password > infrastructure/htpasswd
```

```powershell
# powershell
docker run --rm httpd:2.4-alpine htpasswd -nbB loki-user loki-password | Out-File -Encoding ASCII infrastructure/htpasswd
```

Major dependencies: Spring Boot 4.0 starters (Web MVC, WebFlux, WebSocket, Security, Data JPA, Cache, Actuator),
`springdoc-openapi` (Swagger UI), `jjwt` (JWT), `docker-java` (container management), `influxdb-client-java` (metrics),
`rcon-java` (RCON), JNA (native `cosyfs` binding), Lombok, and PostgreSQL / H2 drivers.

---

## 🏗️ Architecture Overview

The backend uses a **Strategy Pattern** to handle different environments without changing application logic.

- **RuntimeService interface** — the main contract for server management.
  - **DockerRuntimeStrategy** — uses the local Docker socket (`/var/run/docker.sock`). Used for single-node setups.

### File I/O

Unlike traditional cloud apps, Cosy uses **Direct I/O** for file management. Cosy attempts to use the native `cosyfs`
Linux library to ensure secure file operations. On other operating systems this guarantee cannot be made, so a
potentially vulnerable fallback is used instead.

- **Docker mode:** uses Java NIO to read bind mounts directly on the host.

---

## 🗄️ Database Management

### Access the database console

```bash
docker exec -it cosy-dev-postgres psql -U cosy -d cosy
```

Common commands: `\d` (list tables), `SELECT * FROM <table>;` (query), `\q` (exit).

### Reset the database

To reset to a clean state (and re-initialize dummy data), remove the volumes and restart the infrastructure:

```bash
cd infrastructure
docker compose down -v
docker compose up -d
```

---

## 📚 Documentation

Full project documentation lives in the **[Cosy-Docs](https://github.com/Magenta-Mause/Cosy-Docs)** repository and is
published at **[cosy-hosting.net](https://cosy-hosting.net)**.

---

## 🤝 Contributing

Contributions are welcome! Contribution guidelines are maintained **org-wide** in the
[Magenta-Mause/.github](https://github.com/Magenta-Mause/.github) repository.

- **Reporting bugs / requesting features:** all issues for the Cosy project are tracked centrally in the main
  [Magenta-Mause/Cosy](https://github.com/Magenta-Mause/Cosy/issues/new/choose) repository. (Issues opened directly on this
  repo are automatically redirected there.)
- **Development setup:** see [Getting Started](#-getting-started) and [Development](#-development) above.
- **Before pushing:** run `./mvnw spotless:apply` and `./mvnw verify`.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](./LICENSE) file for details.

## 💬 Contact & Support

- **Documentation:** [cosy-hosting.net](https://cosy-hosting.net) / [Cosy-Docs](https://github.com/Magenta-Mause/Cosy-Docs)
- **Issues & questions:** [Magenta-Mause/Cosy issues](https://github.com/Magenta-Mause/Cosy/issues)
- **Organization:** [Magenta-Mause on GitHub](https://github.com/magenta-mause)

---

<sub>Built with 🦆 by the Magenta-Mäuse.</sub>
