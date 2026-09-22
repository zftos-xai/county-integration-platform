$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$pythonCommand = if (Get-Command python3 -ErrorAction SilentlyContinue) { 'python3' }
                 elseif (Get-Command py -ErrorAction SilentlyContinue) { 'py' }
                 else { 'python' }

function Invoke-Checked {
    param([scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "Verification command failed with exit code $LASTEXITCODE"
    }
}

Write-Host 'Checking repository engineering guardrails...'
if ($pythonCommand -eq 'py') {
    Invoke-Checked { py -3 (Join-Path $projectRoot 'plugins/team-engineering-standards/skills/project-engineering-guardrails/scripts/audit_repository.py') $projectRoot --strict }
} else {
    Invoke-Checked { & $pythonCommand (Join-Path $projectRoot 'plugins/team-engineering-standards/skills/project-engineering-guardrails/scripts/audit_repository.py') $projectRoot --strict }
}

Write-Host 'Checking Java package layout...'
Invoke-Checked { node (Join-Path $projectRoot 'tools/verify-java-package-layout.mjs') }

Write-Host 'Checking Controller identity mapping...'
Invoke-Checked { node (Join-Path $projectRoot 'tools/verify-controller-boundaries.mjs') }

Write-Host 'Checking Java comment quality and persistence-model table documentation...'
Invoke-Checked { node (Join-Path $projectRoot 'tools/verify-java-comments.mjs') }

Write-Host 'Checking MyBatis Mapper XML comments...'
Invoke-Checked { node (Join-Path $projectRoot 'tools/verify-mapper-comments.mjs') }

Write-Host 'Checking Java 17 backend...'
Push-Location (Join-Path $projectRoot 'backend')
try {
    Invoke-Checked { mvn -B verify }
} finally {
    Pop-Location
}

Push-Location $projectRoot
try {
    Write-Host 'Checking SQL migration structure...'
    Invoke-Checked { node ./tools/verify-sql-migrations.mjs }
    Invoke-Checked { node ./tools/generate-database-contract-sql.mjs --check }
    Write-Host 'Checking Vue applications...'
    Invoke-Checked { npm run verify:frontend }
} finally {
    Pop-Location
}

Write-Host 'All available project checks passed.'
