$ErrorActionPreference = 'Stop'
if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env. Add GEMINI_API_KEY there to enable Gemini features.'
}
docker compose up -d --build --force-recreate
Start-Process 'http://localhost:3000'
Write-Host 'Study Leftovers is running at http://localhost:3000'
