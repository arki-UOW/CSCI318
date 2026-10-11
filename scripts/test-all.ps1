$ErrorActionPreference = 'Stop'
mvn clean verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
node --test frontend/tests/*.test.cjs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& (Join-Path $PSScriptRoot 'test-launcher.ps1')
