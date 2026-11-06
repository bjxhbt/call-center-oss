. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-DemoEnvironment
$nginxPid = Join-Path $DemoRoot 'runtime/nginx.pid'
if (Test-Path $nginxPid) {
    $nginx = Resolve-DemoNginx
    & $nginx -p ($DemoRoot.Replace('\','/') + '/') -c config/nginx.conf -s quit
    if ($LASTEXITCODE -ne 0) { throw 'Nginx graceful shutdown failed; inspect its PID and logs' }
}
$stateFile = Join-Path $DemoRoot 'runtime/api-process.json'
if (Test-Path $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.pid -ErrorAction SilentlyContinue
    if ($process) {
        if ($process.Path -ne $state.executable -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'PID ownership changed; refusing to stop another process' }
        Stop-Process -Id $process.Id
    }
    Remove-Item -LiteralPath $stateFile
}
Write-Output 'Demo processes stopped. MySQL and Redis were not stopped or modified.'
