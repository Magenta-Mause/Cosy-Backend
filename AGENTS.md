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
| Cosy-Game-Service | Rust (actix-web) game metadata/artwork API (SteamGridDB). **Outdated / no longer used** — the template service now covers this functionality (per the maintainers). |
| Cosy-Template-Service | Go (Gin) service serving the template catalog |
| Cosy-Templates | Curated game/template definitions (schema-validated) |
| Cosy-Minecraft-Integration-Mod | Fabric mod pushing in-game metrics (TPS, players) to the backend |
| Cosy | Meta repo + install scripts |
| Cosy-Docs | Documentation site |
| Cosy-Internal-Deployment | Maintainers' reference Kubernetes deployment |

## Working in this repo

- Java 21 + Maven wrapper: `./mvnw spring-boot:run` (API at http://localhost:8080/api), `./mvnw verify` for tests.
- Dev infrastructure (Postgres, Loki, InfluxDB): `docker compose up -d` in `infrastructure/`.
- **Run `./mvnw spotless:apply` on JDK 21 before pushing.** CI's first step is
  `spotless:check`, so a formatting miss fails the build before any test executes — and
  `./mvnw verify` does **not** cover it. Why, plus the JDK-25 failure mode:
  [SPRING_BOOT.md § Code Style](docs/conventions/SPRING_BOOT.md).
- **Commit messages follow Conventional Commits** (`feat(webhooks):`, `fix:`, `chore(deps):`).
- Local dev login: `admin` / `admin` (see `cosy.defaults` in `application.yaml`).
- **The schema is Flyway-managed** (`ddl-auto: validate`): every schema change needs the
  entity change **plus** a new `V<N>__*.sql` in the same commit, and an already-applied
  migration is never edited. What the CI schema check does and does not catch:
  [SPRING_BOOT.md § Database & JPA](docs/conventions/SPRING_BOOT.md).
- [README.md](README.md) is the human-facing doc. The self-hoster upgrade path, the
  custom-metrics contract, and DB console/reset live there and nowhere else.

## Architecture you should not guess

- **Container work goes through the engine seam.** `EngineManager` (`services/engine`) is
  the interface; `DockerEngineManager` (`services/engine/docker`) is its only
  implementation, selected via the `EngineType` enum. Wire new container behaviour through
  `EngineManager` — don't call the Docker client straight from a service.
- **The backend talks to the Docker socket** (root-equivalent on the host). Treat anything
  touching container lifecycle or host file operations as security-sensitive.
- **Game-server file I/O is native, not plain NIO.** `GameServerFileIoService` and its
  neighbours in `services/core/gameserver` go through a JNA-bound native library
  (`CosyFsHandle` / `CosyFsNative`) for path-safe operations. Where that library is
  unavailable the code falls back to a `SecureDirectoryStream` whose `secure` flag may be
  `false` — the guarantee is genuinely weaker there. Don't swap these for
  `java.nio.file` calls.
- Templates are fetched from the template service (v3 API); descriptions are stored
  raw and untruncated.

## Deep dives — read when your task touches the area

- Writing or reviewing **Spring Boot / Java** code → [docs/conventions/SPRING_BOOT.md](docs/conventions/SPRING_BOOT.md)
- Touching **auth** (login, JWT, tokens, security filters) → [docs/conventions/AUTH.md](docs/conventions/AUTH.md)

**If you change a convention documented here, update the corresponding doc in the same PR.**
