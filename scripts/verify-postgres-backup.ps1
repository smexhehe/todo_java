param(
    [Parameter(Mandatory)]
    [string] $BackupFile,
    [string] $Service = "postgres",
    [string] $RestoreDatabase = "todo_restore_check",
    [switch] $KeepRestoredDatabase
)

$ErrorActionPreference = "Stop"

$backup = Get-Item -LiteralPath $BackupFile -ErrorAction Stop
$containerPath = "/tmp/restore-check-$([guid]::NewGuid().ToString('N')).dump"
$existing = & docker compose exec -T $Service psql -U todo -d postgres -At -c "SELECT datname FROM pg_database WHERE datname = '$RestoreDatabase';"
if ($existing) {
    throw "Database '$RestoreDatabase' already exists. Choose another name; this script will not overwrite it."
}

try {
    & docker compose cp $backup.FullName "${Service}:$containerPath"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy '$($backup.Name)' into container '$Service'." }

    & docker compose exec -T $Service createdb -U todo $RestoreDatabase
    if ($LASTEXITCODE -ne 0) { throw "Could not create restore database '$RestoreDatabase'." }

    & docker compose exec -T $Service pg_restore -U todo -d $RestoreDatabase --no-owner --no-privileges --exit-on-error $containerPath
    if ($LASTEXITCODE -ne 0) { throw "Restore into '$RestoreDatabase' failed." }

    & docker compose exec -T $Service psql -U todo -d $RestoreDatabase -c "SELECT count(*) AS restored_tasks FROM scheduler;"
    Write-Host "Restore verification succeeded: $($backup.Name) -> $RestoreDatabase"
}
finally {
    if (-not $KeepRestoredDatabase) {
        & docker compose exec -T $Service psql -U todo -d postgres -c "DROP DATABASE IF EXISTS $RestoreDatabase;" | Out-Null
    }
    & docker compose exec -T $Service rm -f $containerPath | Out-Null
}