$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host 'Checking Java 17 backend...'
Push-Location (Join-Path $projectRoot 'backend')
try {
    mvn -B verify
} finally {
    Pop-Location
}

Write-Host 'Checking Vue applications...'
Push-Location $projectRoot
try {
    Write-Host 'Checking SQL migration structure...'
    node ./tools/verify-sql-migrations.mjs
    Write-Host 'Checking frontend documentation comments...'
    node ./tools/verify-frontend-comments.mjs
    npm run typecheck
    npm run build
} finally {
    Pop-Location
}

Write-Host 'All available project checks passed.'
