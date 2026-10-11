param([int]$StartupTimeoutSeconds = 180, [switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.env')) {
        Copy-Item -LiteralPath '.env.example' -Destination '.env'
        Write-Host 'Created .env. Add your private provider key there to enable AI features.'
    }
    & docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Docker startup failed (exit $LASTEXITCODE). Check Docker Desktop and the preceding error; the application has not been verified as running." }
    $pending = @(
        'http://localhost:8085/api/auth/session', 'http://localhost:8081/api/subjects',
        'http://localhost:8082/api/assessments', 'http://localhost:8083/api/study-sessions',
        'http://localhost:8084/api/planning/ai/status', 'http://localhost:3000'
    )
    $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
    do {
        $pending = @($pending | Where-Object {
            $url = $_
            $status = 0
            try { $status = [int](Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2).StatusCode }
            catch { if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode } }
            # Protected controllers must be reachable without disclosing or supplying a token.
            if ($url -eq 'http://localhost:3000') { $status -ne 200 }
            else { $status -notin @(400, 401) }
        })
        if ($pending.Count -eq 0) { break }
        if ((Get-Date) -ge $deadline) { throw "Startup readiness timed out. Unavailable endpoints: $($pending -join ', '). Inspect docker compose logs; the application is not ready." }
        Start-Sleep -Seconds 2
    } while ($true)
    if (-not $NoBrowser) { Start-Process 'http://localhost:3000' }
    Write-Host 'Study Leftovers is ready at http://localhost:3000. AI features require your privately configured provider.'
} finally { Pop-Location }
