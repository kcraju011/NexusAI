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
