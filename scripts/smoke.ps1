param(
    [string]$BaseUrl = 'http://127.0.0.1:7540',
    [string]$Token = ''
)

$ErrorActionPreference = 'Stop'
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
if ($Token) {
    $hostName = ([uri]$BaseUrl).Host
    $session.Cookies.Add((New-Object System.Net.Cookie('token', $Token, '/', $hostName)))
}
$created = @()

try {
    $page = Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing
    if ($page.StatusCode -ne 200 -or $page.Content -notmatch 'html') {
        throw 'Static index page is unavailable'
    }

    $next = Invoke-WebRequest -Uri "$BaseUrl/api/nextdate?now=20240126&date=20240113&repeat=d%207" -UseBasicParsing
    if ($next.Content -ne '20240127') { throw "Unexpected next date: $($next.Content)" }

    $body = @{ date = '21000101'; title = 'smoke task'; comment = 'created by smoke test'; repeat = '' } | ConvertTo-Json -Compress
    $createdTask = Invoke-RestMethod -Uri "$BaseUrl/api/task" -Method Post -WebSession $session -ContentType 'application/json' -Body $body
    $id = [string]$createdTask.id
    $created += $id
    $task = Invoke-RestMethod -Uri "$BaseUrl/api/task?id=$id" -WebSession $session
    if ($task.id -ne $id -or $task.title -ne 'smoke task') { throw 'Create/get response mismatch' }

    $body = @{ id = $id; date = '21000101'; title = 'smoke updated'; comment = ''; repeat = '' } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "$BaseUrl/api/task" -Method Put -WebSession $session -ContentType 'application/json' -Body $body | Out-Null
    $listed = Invoke-RestMethod -Uri "$BaseUrl/api/tasks?search=smoke%20updated" -WebSession $session
    if (@($listed.tasks | Where-Object { $_.id -eq $id }).Count -ne 1) { throw 'Update/search response mismatch' }

    $body = @{ date = '21000101'; title = 'smoke recurring'; comment = ''; repeat = 'd 1' } | ConvertTo-Json -Compress
    $recurring = Invoke-RestMethod -Uri "$BaseUrl/api/task" -Method Post -WebSession $session -ContentType 'application/json' -Body $body
    $recurringId = [string]$recurring.id
    $created += $recurringId
    Invoke-RestMethod -Uri "$BaseUrl/api/task/done?id=$recurringId" -Method Post -WebSession $session | Out-Null
    $afterDone = Invoke-RestMethod -Uri "$BaseUrl/api/task?id=$recurringId" -WebSession $session
    if ($afterDone.date -ne '21000102') { throw 'Recurring task was not moved' }

    Write-Output 'Smoke test passed: static page, next date, create, get, update, search, done.'
}
finally {
    foreach ($id in $created) {
        Invoke-RestMethod -Uri "$BaseUrl/api/task?id=$id" -Method Delete -WebSession $session | Out-Null
    }
}
