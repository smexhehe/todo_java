param(
    [string] $EnvFile = ".env.production",
    [int] $TimeoutSeconds = 90,
    [string] $HealthUrl = "http://127.0.0.1:7540/actuator/health"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file '$EnvFile' was not found. Copy .env.production.example first."
}

$compose = @("--env-file", $EnvFile, "-f", "docker-compose.yml", "-f", "docker-compose.prod.yml")

Write-Host "Pulling the release image..."
& docker compose @compose pull todo
if ($LASTEXITCODE -ne 0) { throw "Image pull failed." }

Write-Host "Starting Todo and its dependencies..."
& docker compose @compose up -d --no-build todo
if ($LASTEXITCODE -ne 0) { throw "Container startup failed." }

$healthUrl = $HealthUrl
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

Write-Host "Waiting for $healthUrl ..."
do {
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            Write-Host "Deployment is healthy: HTTP 200"
            exit 0
        }
    } catch {
        # The application can still be starting.
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)

& docker compose @compose logs --tail 100 todo
throw "The application did not become healthy within $TimeoutSeconds seconds."