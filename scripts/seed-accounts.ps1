[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sqlFile = Join-Path $PSScriptRoot 'seed-accounts.sql'

if (!(Test-Path -LiteralPath $sqlFile)) {
    throw "Missing SQL script: $sqlFile"
}

Write-Host "=== Seeding SportHub Test Accounts ===" -ForegroundColor Cyan

# 1. Find postgres container
$container = (docker ps --filter "name=postgres" --format "{{.Names}}" | Select-Object -First 1)

if ($container) {
    Write-Host "Found Docker PostgreSQL container: $container" -ForegroundColor Green
    
    # Read user from .env if present
    $envPath = Join-Path $root '.env'
    $pgUser = 'sporthub_demo'
    if (Test-Path -LiteralPath $envPath) {
        $envLines = Get-Content $envPath
        foreach ($line in $envLines) {
            if ($line -match '^POSTGRES_USER=(.*)$') {
                $pgUser = $Matches[1].Trim()
            }
        }
    }

    Get-Content $sqlFile -Raw | docker exec -i $container psql -U $pgUser -d identity_db
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host " Successfully seeded all test accounts in identity_db!" -ForegroundColor Green
    } else {
        throw "Failed to execute seed script inside container $container"
    }
} else {
    Write-Warning "PostgreSQL container not found. Make sure Docker is running ('docker compose up -d')."
}

Write-Host "`nTest accounts ready with password: SportHub123@" -ForegroundColor Yellow
Write-Host "1. Admin:    admin@sporthub.local"
Write-Host "2. Owner:    owner@sporthub.local"
Write-Host "3. Staff:    staff@sporthub.local"
Write-Host "4. Customer: customer@sporthub.local / customer2@sporthub.local`n"
