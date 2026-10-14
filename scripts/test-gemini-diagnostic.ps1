# No Google requests or real keys: child PowerShell processes stub only the HTTP boundary.
$ErrorActionPreference = 'Stop'
$sandbox = Join-Path ([IO.Path]::GetTempPath()) ('study-gemini-test-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $sandbox | Out-Null
$runner = Join-Path $sandbox 'runner.ps1'
$diagnostic = Join-Path $PSScriptRoot 'check-gemini.ps1'
$template = @'
param($Scenario, $Diagnostic)
$env:GEMINI_API_KEY = ''
$env:GEMINI_MODEL = ''
function Test-Path { return $true }
function Get-Content {
    if ($Scenario -ne 'missing') {
        'GEMINI_API_KEY="dummy-private-key"'
        'GEMINI_MODEL=test-model'
    }
}
function Invoke-RestMethod {
    param($Method, $Uri, $Headers, $ContentType, $Body, $TimeoutSec)
    if ($Headers['x-goog-api-key'] -ne 'dummy-private-key') { throw 'Wrong key' }
    if ($Uri -ne 'https://generativelanguage.googleapis.com/v1beta/models/test-model:generateContent') { throw 'Wrong endpoint' }
    if (($Body | ConvertFrom-Json).generationConfig.responseMimeType -ne 'application/json') { throw 'Wrong JSON mode' }
    if ($Scenario -eq 'success') {
        return @{ candidates = @(@{content = @{parts = @(@{text = '{"ok":true}'})}}) }
    }
    if ($Scenario -eq 'invalid-json') {
        return @{ candidates = @(@{content = @{parts = @(@{text = 'invalid'})}}) }
    }
    $exception = New-Object System.Exception 'Raw private exception dummy-private-key'
    $exception | Add-Member NoteProperty Response ([pscustomobject]@{StatusCode = [int]$Scenario})
    $record = New-Object System.Management.Automation.ErrorRecord($exception, 'ProviderError', 'InvalidOperation', $null)
    $record.ErrorDetails = New-Object System.Management.Automation.ErrorDetails('{"error":{"message":"Temporary error dummy-private-key"}}')
    throw $record
}
& $Diagnostic -EnvFile unused
exit $LASTEXITCODE
'@
Set-Content -LiteralPath $runner -Value $template
try {
    $engine = (Get-Process -Id $PID).Path
    foreach ($case in @(
        @{Scenario='missing'; Code=2; Expected='No request sent'},
        @{Scenario='success'; Code=0; Expected='PASS:'},
        @{Scenario='503'; Code=1; Expected='Google returned HTTP 503'},
        @{Scenario='429'; Code=1; Expected='Google returned HTTP 429'},
        @{Scenario='invalid-json'; Code=1; Expected='Could not complete'}
    )) {
        $output = (& $engine -NoProfile -File $runner -Scenario $case.Scenario -Diagnostic $diagnostic 2>&1) -join [Environment]::NewLine
        if ($LASTEXITCODE -ne $case.Code -or -not $output.Contains($case.Expected)) {
            throw "Unexpected result for $($case.Scenario): $output"
        }
        if ($output.Contains('dummy-private-key')) { throw 'Diagnostic leaked a key' }
        if ($case.Scenario -eq '503' -and -not $output.Contains('[REDACTED]')) { throw 'Expected redacted provider detail' }
    }
    Write-Output 'PASS: missing key, success, upstream 503/429, malformed output and key redaction'
} finally {
    # Only remove the exact temporary files created above.
    Remove-Item -LiteralPath $runner -Force
    Remove-Item -LiteralPath $sandbox
}
