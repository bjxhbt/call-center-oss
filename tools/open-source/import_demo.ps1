param(
    [string]$BaseUrl = 'http://127.0.0.1:4320',
    [long]$GroupId = 1,
    [long]$SkillId = 1
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:OCH_ACCESS_TOKEN)) {
    throw 'Set OCH_ACCESS_TOKEN to a logged-in administrator token'
}
$projectRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$flow = Get-Content -LiteralPath (Join-Path $projectRoot 'examples/public-demo/ivr-business-hours.json') -Raw -Encoding UTF8 | ConvertFrom-Json
($flow.nodes | Where-Object id -eq 'support').properties.routeValue = [string]$SkillId
$body = @{
    groupId = $GroupId
    name = 'Public business-hours demo'
    desc = '09:00-18:00 server time, transfer to the selected demo skill'
    status = 0
    flowData = ($flow | ConvertTo-Json -Depth 30 -Compress)
} | ConvertTo-Json -Depth 35 -Compress
$response = Invoke-RestMethod -Method Post -Uri ($BaseUrl.TrimEnd('/') + '/call/v1/ivr/add') `
    -Headers @{ Authorization = 'Bearer ' + $env:OCH_ACCESS_TOKEN } `
    -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
if ($response.code -ne 200) { throw 'IVR import failed; inspect the API response in the application' }
Write-Output 'Demo imported as a draft. Review the flow and skill membership before publishing it.'
