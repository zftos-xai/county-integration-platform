$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host 'Checking Java package layout...'
node (Join-Path $projectRoot 'tools/verify-java-package-layout.mjs')

Write-Host 'Checking Java comment quality and persistence-model table documentation...'
node (Join-Path $projectRoot 'tools/verify-java-comments.mjs')

Write-Host 'Checking MyBatis Mapper XML comments...'
node (Join-Path $projectRoot 'tools/verify-mapper-comments.mjs')

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
    node ./tools/generate-database-contract-sql.mjs --check
    Write-Host 'Checking frontend documentation comments...'
    node ./tools/verify-frontend-comments.mjs
    npm run typecheck
    npm run build
} finally {
    Pop-Location
}

Write-Host 'All available project checks passed.'
