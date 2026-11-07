[![NexusAI CI](https://github.com/kcraju011/NexusAI/actions/workflows/ci.yml/badge.svg)](https://github.com/kcraju011/NexusAI/actions/workflows/ci.yml)

## Local development

The API listens on port `8080`. To run the complete stack, start Docker Desktop,
set the required values in a root `.env` file (`POSTGRES_DB`, `POSTGRES_USER`,
`POSTGRES_PASSWORD`, `NEXUS_ADMIN_USERNAME`, `NEXUS_ADMIN_PASSWORD`,
`NEXUS_VIEWER_USERNAME`, and `NEXUS_VIEWER_PASSWORD`), then run from the
repository root:

```powershell
docker compose up --build -d
docker compose ps
```

Wait until the `postgres`, `redis`, and `api` services are running. Then verify
the API and its trace response header:

```powershell
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/health"
$response.Headers["X-Trace-Id"]
```

The incident schema and lifecycle migration are initialized automatically for
a new PostgreSQL volume. Existing volumes are not re-initialized; apply the V2
migration without deleting the database volume:

```powershell
Get-Content .\docs\migrations\V2__incident_lifecycle.sql -Raw |
    docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
```

If `Invoke-WebRequest` reports that it cannot connect, the API is not listening
on port `8080`; check that Docker Desktop is running and inspect service startup
logs with `docker compose logs api`.

To run the API directly on the host instead, start PostgreSQL and Redis
separately, configure `NEXUS_DB_URL`, `NEXUS_DB_USER`, `NEXUS_DB_PASSWORD`,
`REDIS_URI`, `NEXUS_ADMIN_USERNAME`, `NEXUS_ADMIN_PASSWORD`,
`NEXUS_VIEWER_USERNAME`, and `NEXUS_VIEWER_PASSWORD`, then start
`com.nexusai.core.ApiMain`. For Redis, set its URI in PowerShell:

```powershell
$env:REDIS_URI = "redis://localhost:6379"
```

When running with Docker Compose, the API uses the Redis service name:
`redis://redis:6379`.

## Dashboard incident and trace workflow

Open the dashboard at `http://localhost:5173` and sign in with the
`NEXUS_ADMIN_USERNAME` and `NEXUS_ADMIN_PASSWORD` values from `.env`. Create an
incident from the dashboard, then select **Correlation** in its row to link a
32-character hexadecimal trace ID and its service, operation, error type, and
message. Use **Retrieve correlation** to load the linked details again.

Correlation links are currently held in API memory and are cleared when the API
restarts.
