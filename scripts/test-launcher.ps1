# Isolated launcher behavior tests: no Docker, browser, private files or containers are touched.
$ErrorActionPreference = 'Stop'
$source = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'run-all.ps1')
# The tested script's location is supplied without changing its executable statements.
$scriptBlock = [scriptblock]::Create($source.Replace('$PSScriptRoot', "'$PSScriptRoot'"))
function Test-Path { return $true }
function Push-Location { }
function Pop-Location { }
function Start-Sleep { }
function Start-Process { $script:browserOpened = $true }
function docker { $global:LASTEXITCODE = $script:dockerExit }
# Explicit parameter binding for stubbed health requests.
function Invoke-WebRequest { param($Uri, [switch]$UseBasicParsing, $TimeoutSec)
    if ($script:ready) { return @{StatusCode = $(if ($Uri -eq 'http://localhost:3000') {200} else {401})} }
    throw 'offline'
}
$script:browserOpened = $false
$script:dockerExit = 1
try { & $scriptBlock; throw 'Expected Docker failure' } catch { if ($_.Exception.Message -notlike 'Docker startup failed*') { throw } }
if ($script:browserOpened) { throw 'Browser opened after Docker failure' }
$script:dockerExit = 0
$script:ready = $false
try { & $scriptBlock -StartupTimeoutSeconds 0; throw 'Expected readiness failure' } catch { if ($_.Exception.Message -notlike 'Startup readiness timed out*') { throw } }
if ($script:browserOpened) { throw 'Browser opened before readiness' }
$script:ready = $true
& $scriptBlock
if (-not $script:browserOpened) { throw 'Browser did not open after readiness' }
Write-Host 'PASS: Docker failure, readiness timeout and successful launcher startup'
