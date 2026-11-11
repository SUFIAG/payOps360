# Recreates the local development database. All data in it is lost.
# The demo tenant is re-seeded automatically on the next backend start (dev profile).
param(
    [string]$Database = "payops360",
    [string]$User = "postgres",
    [string]$PgHost = "localhost",
    [string]$Psql = "psql"
)

$ErrorActionPreference = "Stop"
if ($Database -notmatch '^[a-z0-9_]+$') { throw "Invalid database name" }

& $Psql -U $User -h $PgHost -d postgres -v ON_ERROR_STOP=1 `
    -c "DROP DATABASE IF EXISTS $Database WITH (FORCE)" `
    -c "CREATE DATABASE $Database TEMPLATE template0 ENCODING 'UTF8'"
if ($LASTEXITCODE -ne 0) { throw "psql failed" }
Write-Host "Database '$Database' recreated. Start the backend to seed demo data."
