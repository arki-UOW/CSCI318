param(
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env'),
    [string]$Model = ''
)

$ErrorActionPreference = 'Stop'
# Read only the two configuration values needed. Never execute the .env file.
$settings = @{}
if (Test-Path -LiteralPath $EnvFile) {
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match '^\s*(GEMINI_API_KEY|GEMINI_MODEL)\s*=\s*(.*?)\s*$') {
            $name = $matches[1]
            $value = $matches[2]
            if ($value -match '^"(.*)"\s*(?:#.*)?$' -or $value -match "^'(.*)'\s*(?:#.*)?$") {
                $value = $matches[1]
            } else {
                $value = ($value -replace '\s+#.*$', '').Trim()
            }
            $settings[$name] = $value
        }
    }
}
$apiKey = $env:GEMINI_API_KEY
if ([string]::IsNullOrWhiteSpace($apiKey)) { $apiKey = $settings['GEMINI_API_KEY'] }
if ([string]::IsNullOrWhiteSpace($apiKey) -or $apiKey -eq 'your_key_here') {
    Write-Output 'No GEMINI_API_KEY found in the process environment or private .env. No request sent.'
    exit 2
}
$apiKey = $apiKey.Trim()
if ([string]::IsNullOrWhiteSpace($Model)) { $Model = $env:GEMINI_MODEL }
if ([string]::IsNullOrWhiteSpace($Model)) { $Model = $settings['GEMINI_MODEL'] }
if ([string]::IsNullOrWhiteSpace($Model)) { $Model = 'gemini-3.6-flash' }
$Model = $Model.Trim()
if ($Model -notmatch '^[a-zA-Z0-9._-]+$') {
    Write-Output 'GEMINI_MODEL must be a model ID, without a URL or models/ prefix.'
    exit 2
}

Write-Output "Testing Gemini model: $Model"
Write-Output 'One small JSON-generation request will be sent to Google. No documents or key values are printed.'
$body = @{
    contents = @(@{ role = 'user'; parts = @(@{ text = 'Return only this JSON object: {"ok":true}' }) })
    generationConfig = @{ responseMimeType = 'application/json'; maxOutputTokens = 8192 }
} | ConvertTo-Json -Depth 8 -Compress
try {
    $result = Invoke-RestMethod -Method Post `
        -Uri "https://generativelanguage.googleapis.com/v1beta/models/${Model}:generateContent" `
        -Headers @{ 'x-goog-api-key' = $apiKey } -ContentType 'application/json' `
        -Body $body -TimeoutSec 120
    $answer = ($result.candidates[0].content.parts | ForEach-Object { $_.text }) -join ''
    $parsed = $answer | ConvertFrom-Json
    if ($parsed.ok -ne $true) { throw 'Unexpected response' }
    Write-Output 'PASS: Google accepted the key/model and returned the expected JSON.'
    Write-Output 'This verifies a live minimal request, not document extraction or planning. Retry the outline in the app next.'
    exit 0
} catch {
    $status = $null
    if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    if ($status) {
        Write-Output "FAIL: Google returned HTTP $status (the upstream status, not the app status)."
    } else {
        Write-Output 'FAIL: Could not complete the request or validate the returned JSON.'
    }
    # Never dump the exception, request headers, full response or URL query strings.
    try {
        $providerError = ($_.ErrorDetails.Message | ConvertFrom-Json).error
        if ($providerError.message) {
            $safeMessage = ([string]$providerError.message).Replace($apiKey, '[REDACTED]')
            $safeMessage = $safeMessage -replace 'AIza[\w-]+', '[REDACTED]'
            $safeMessage = $safeMessage -replace '(?i)(key=)[^\s&"<>]+', '$1[REDACTED]'
            $safeMessage = $safeMessage -replace '[\r\n\x00-\x1F]', ' '
            Write-Output ('Google message: ' + $safeMessage.Substring(0, [Math]::Min(1000, $safeMessage.Length)))
        }
    } catch { }
    switch ($status) {
        400 { Write-Output 'Check the Google message for an invalid key, unsupported parameters, regional restrictions or billing prerequisites.' }
        401 { Write-Output 'Google rejected authentication. Check the private key on this computer.' }
        403 { Write-Output 'Check API/project permissions and key restrictions in Google AI Studio.' }
        404 { Write-Output 'Check that this model supports generateContent and is available to this API project.' }
        429 { Write-Output 'Check quota/rate limits in Google AI Studio; wait before retrying.' }
        503 { Write-Output 'Google is unavailable or overloaded. Wait before retrying; this is not proof of an invalid key.' }
        default { Write-Output 'Check Google service status and connectivity. If this probe passes but the app fails, check the running container configuration and request.' }
    }
    exit 1
}
