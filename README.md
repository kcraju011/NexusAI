[![NexusAI CI](https://github.com/kcraju011/NexusAI/actions/workflows/ci.yml/badge.svg)](https://github.com/kcraju011/NexusAI/actions/workflows/ci.yml)

## Local development

When running the API without Docker, start Redis separately and set its URI in
PowerShell before starting the application:

```powershell
$env:REDIS_URI = "redis://localhost:6379"
```

When running with Docker Compose, the API uses the Redis service name:
`REDIS_URI: redis://redis:6379`.
