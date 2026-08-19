$ErrorActionPreference = 'Stop'
$subjectId = Read-Host 'Paste a confirmed subject UUID'
$today = Get-Date -Format 'yyyy-MM-dd'
$due = (Get-Date).AddDays(5).ToString('yyyy-MM-dd')
$assessment = @{subjectId=$subjectId;title='Prototype Demonstration';type='Presentation';weighting=25;dueDate=$due;estimatedMinutes=300;priority='HIGH'} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/api/assessments' -ContentType 'application/json' -Body $assessment
$session = @{subjectId=$subjectId;durationMinutes=45;studyDate=$today;description='Prepared the prototype demonstration'} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri 'http://localhost:8083/api/study-sessions' -ContentType 'application/json' -Body $session
Invoke-RestMethod 'http://localhost:8084/api/planning/this-week'
