# Cosy Backend

Cosy ("Cost Optimized Server Yard") is a **community-driven, open-source platform for
hosting game servers on your own hardware**. There is no central Cosy deployment:
every user runs their own instance (via Docker Compose or Kubernetes) and Cosy manages
game servers as Docker containers on that machine — container lifecycle, real-time
metrics/logs, file browsing, user & quota management, RCON, and webhooks.

Cosy is a multi-repo project under [github.com/Magenta-Mause](https://github.com/Magenta-Mause):

| Repo | Role |
|---|---|
| **Cosy-Backend** (this repo) | Core orchestration engine: Spring Boot / Java 21, Docker socket, JWT auth, WebSockets, RCON, PostgreSQL |
| Cosy-Frontend | React 19 + TypeScript web UI (Bun + Vite), consumes this repo's OpenAPI spec |
| Cosy-Game-Service | Rust (actix-web) game metadata/artwork API (SteamGridDB) |
| Cosy-Template-Service | Go (Gin) service serving the template catalog |
| Cosy-Templates | Curated game/template definitions (schema-validated) |
| Cosy-Minecraft-Integration-Mod | Fabric mod pushing in-game metrics (TPS, players) to the backend |
| Cosy / Cosy-Docs / Cosy-Internal-Deployment | Meta repo + install scripts / docs site / maintainers' reference k8s deployment |

## Working in this repo

- Java 21 + Maven wrapper: `./mvnw spring-boot:run` (API at http://localhost:8080/api), `./mvnw verify` for tests.
- Dev infrastructure (Postgres, Loki, InfluxDB): `docker compose up -d` in `infrastructure/`.
- Local dev login: `admin` / `admin` (see `cosy.defaults` in `application.yaml`).
- **The schema is Flyway-managed** (`ddl-auto: validate`). Every schema change needs the
  entity change **plus** a new `V<N>__*.sql` in `src/main/resources/db/migration/` in the
  same commit. Never edit an already-applied migration — add a new one.
- The CI drift guard (`FlywayMigrationTest`, gated by `CI_PG_TESTS=true`) boots the app
  against a real Postgres and fails the PR if entities and migrations disagree.
- The backend talks to the Docker socket (root-equivalent on the host). Treat anything
  touching container lifecycle or host file operations as security-sensitive.
- Templates are fetched from the template service (v3 API); descriptions are stored
  raw and untruncated.

## Deep dives — read when your task touches the area

- Writing or reviewing **Spring Boot / Java** code → [docs/conventions/SPRING_BOOT.md](docs/conventions/SPRING_BOOT.md)
- Touching **auth** (login, JWT, tokens, security filters) → [docs/conventions/AUTH.md](docs/conventions/AUTH.md)
