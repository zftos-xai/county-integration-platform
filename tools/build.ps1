$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Push-Location (Join-Path $projectRoot 'backend')
try {
    mvn -B clean package
} finally {
    Pop-Location
}

Push-Location $projectRoot
try {
    npm run build
} finally {
    Pop-Location
}
