param(
    [string] $Service = "postgres",
    [string] $Database = "todo",
    [string] $OutputDirectory = "diagnostics/backups"
)

$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$outputPath = Join-Path $OutputDirectory "$Database-$stamp.dump"
$containerPath = "/tmp/$Database-$stamp.dump"

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

try {
    & docker compose exec -T $Service pg_dump -U todo -d $Database -Fc -f $containerPath
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed for database '$Database'." }

    & docker compose cp "${Service}:$containerPath" $outputPath
    if ($LASTEXITCODE -ne 0) { throw "Could not copy the backup from container '$Service'." }

    $backup = Get-Item -LiteralPath $outputPath
    Write-Host "Backup created: $($backup.FullName) ($($backup.Length) bytes)"
}
finally {
    & docker compose exec -T $Service rm -f $containerPath | Out-Null
}