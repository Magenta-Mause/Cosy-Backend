# **🏡 Cosy - Backend**

The core orchestration engine for **Cosy** (Cost Optimised Server Yard).  
This Spring Boot application acts as the "Town Hall" of the Cosy ecosystem. It manages game server containers, handles
file I/O operations directly on the host, and coordinates with specialized data stores for metrics, logs, and backups.

## **🛠️ Tech Stack**

* **Language:** Java 21
* **Framework:** Spring Boot 3.x
* **Build Tool:** Maven
* **Database:** PostgreSQL (Data), InfluxDB (Metrics), Grafana Loki (Logs)
* **Runtime Support:** Docker (Native)

## **📋 Prerequisites**

* **Java 21 SDK** (Required)
* **Docker Desktop / Engine** (Required for the runtime & dev infrastructure)
* **Maven** (Optional, using the wrapper is recommended)

## **🚀 Getting Started**

### **1. Start Infrastructure**

Before running the backend, you need the "Data Silos" (Postgres, Influx, etc.) running. We provide a docker-compose file
for development.

```shell
cd infrastructure  
docker compose up -d
```

### **2. Configuration**

Ensure your application.properties or application.yaml is configured to connect to the services started in step 1\.

### **3. Build & Run**

You can start the application using the Maven wrapper:

```shell
./mvnw spring-boot:run
```

The API will be available at <http://localhost:8080/api>.

## Custom Metrics (Game Server → Cosy Backend)

Cosy supports **custom, game-specific metrics** published directly by your game server (for example via a Minecraft mod/plugin). This is useful for values that Cosy cannot collect automatically, such as:

* `playerCount`
* `tps`
* `mspt`
* current game state / map name
* modpack-specific stats

The server publishes a JSON object (a simple key/value map). Cosy stores this as the server’s *current custom metrics* and will use it for display/streaming.

### 1) Required environment variables

Your game server process/container must have these environment variables set (your mod/plugin reads them at runtime):

* `COSY_BACKEND_URL`  
  Base URL of the Cosy backend (e.g. `https://<your-domain>`)

* `COSY_GAMESERVER_UUID`  
  The UUID of this game server in Cosy

* `COSY_CONTAINER_SECRET`  
  Secret used to authenticate custom metric updates

> Tip: Read these once at server startup and fail fast (log a clear error) if any are missing.

### 2) Validate credentials (GET)

Before publishing metrics, verify that your credentials are correct by calling the validation endpoint:

* **Method:** `GET`
* **Goal:** Ensure the backend is reachable and that the `(uuid, secret)` pair is accepted.

If validation fails (non-2xx), do not spam updates. Log the error and retry with backoff.

> Note: The exact endpoint path depends on your backend API routes. Use the validation endpoint exposed by the Cosy backend (see the Metrics/GameServer controllers in this repo).

### 3) Publish custom metrics (PUT)

To publish or update custom metrics, send the current metrics map to Cosy:

Use the following endpoints:

PUT `/api/internal/game-server/custom-metric/{game-server-uuid}`

Body: JSON object (flat key/value map)

```json
{
  "playerCount": 12,
  "tps": 19.8,
  "mspt": 5.3,
  "motd": "Vanilla+ SMP",
  "pvpEnabled": true
}
```

Headers:

```
Authorization: `{secret}`
```

Response: 2xx

GET `api/internal/game-server/test-connection/{game-server-uuid}`

Headers:

```
Authorization: `{secret}`
```

Response 2xx with body:

```json
{
  "data": false, // <-- true if connection successful
  "error": null,
  "path": "/api/internal/game-server/test-connection/f98585aa-78d0-4fdf-9b3b-2f4b8c66d6e0",
  "status_code": 200,
  "success": true,
  "timestamp": "2026-02-18T20:29:54.046171033Z"
}
```

Cosy treats this payload as the server’s **current custom metric holder**. Publish on an interval (e.g. every 5–10 seconds) and/or when values change.

### Suggested workflow (pseudo-code)

```text
onServerStart: 
  url = env("COSY_BASE_URL")
  uuid = env("COSY_GAMESERVER_UUID")
  secret = env("COSY_CONTAINER_SECRET")
  GET url + <validate-endpoint> using uuid + secret 
  if not ok:
   throw error
every 5s: 
  metrics = { "playerCount": getOnlinePlayers(), "tps": getTps(), "mspt": getMspt() }
  PUT url + <custom-metrics-endpoint> using uuid + secret body = metrics
```

### Recommendations

* Keep metric keys stable (e.g. always `playerCount`, not sometimes `players`).
* Use primitive values: **number**, **string**, **boolean**.

## 🛜 Dependencies

Cosy uses a Postgres instance for data storage and Loki for Server Logs.
The Postgres setup is straightforward and can be found in the `infrastructure` folder.
The Loki setup is a bit more involved as Loki itself doesn't have any authorization mechanism, so we use an nginx
reverse proxy in front of it to add basic auth. For this inside this Repository we have a `infrastructure/htpasswd` file checked in, which contains a development username/password: [user: `loki-user`, password: `loki-password`].
This needs to be changed in prod. For this you can use this command to generate a password hashed htpasswd file:
powershell:

```powershell
docker run --rm httpd:2.4-alpine htpasswd -nbB loki-user loki-password | Out-File -Encoding ASCII htpasswd
```

bash:

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbB loki-user loki-password > infrastructure/htpasswd
```

## **🧹 Code Style & Linting**

We maintain a strict code style using **Spotless** and **Google Java Format (AOSP style)**.

### **Check Code Style**

To verify if your code meets the standards without modifying it:

```shell
./mvnw spotless:check
```

### **Fix Code Style**

To automatically format your code:

```shell
./mvnw spotless:apply
```

**Pro Tip:** It is highly recommended to run `spotless:apply` before pushing any code to avoid CI failures.

## **🏗️ Architecture Overview**

The backend uses a **Strategy Pattern** to handle different environments without changing application logic.

* **RuntimeService Interface:** The main contract for server management.
  * **DockerRuntimeStrategy:** Uses the local Docker Socket (/var/run/docker.sock). Used for single-node setups.

### **File I/O**

Unlike traditional cloud apps, Cosy uses **Direct I/O** for file management.
Cosy will attempt to utilize a native linux library to ensure secure file operations. When running on other operating systems, this
security cannot be ensured so a potentially vulnerable fallback will be used.

* **Docker Mode:** Uses Java NIO to read Bind Mounts directly on the host.

## **🧪 Testing**

Run unit and integration tests:

```
./mvnw test
```

## **📄 API Documentation**

Once the application is running, you can explore the REST API via Swagger UI:

* <http://localhost:8080/api/swagger-ui/index.html>

## **🗄️ Database Management**

### **Access Database Console**

To access the PostgreSQL database running in Docker:

```shell
docker exec -it cosy-postgres psql -U cosy -d cosy
```

**Common Commands:**

* `\d` - List all tables, views, and sequences
* `SELECT * FROM <table_name>;` - Query data
* `\q` - Exit psql

### **Reset Database**

To avoid DB corruption or reset to a clean state (re-initialize dummy data), remove the volumes and restart the
infrastructure:

```shell
cd infrastructure
docker compose down -v
docker compose up -d
```
