param([int]$TimeoutSeconds = 120)
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-DemoEnvironment
$java = Resolve-DemoJava
$nginx = Resolve-DemoNginx
foreach ($port in @($env:OCH_API_PORT, $env:OCH_FILE_PORT, $env:OCH_WEB_PORT)) {
    if (Test-DemoPort '127.0.0.1' ([int]$port)) { throw "Port $port is already in use; no processes were stopped" }
}
foreach ($dependency in @(@($env:OCH_DB_HOST,$env:OCH_DB_PORT), @($env:OCH_REDIS_HOST,$env:OCH_REDIS_PORT))) {
    if (-not (Test-DemoPort $dependency[0] ([int]$dependency[1]))) { throw "Dependency is unreachable: $($dependency[0]):$($dependency[1])" }
}
if (-not $env:OCH_DB_PASSWORD) { $env:OCH_DB_PASSWORD = Read-DemoSecret 'MySQL demo database password' }
if (-not $env:OCH_DB_PASSWORD) { throw 'OCH_DB_PASSWORD must not be empty' }
Write-DemoNginxConfig
$prefix = $DemoRoot.Replace('\','/') + '/'
& $nginx -p $prefix -c config/nginx.conf -t
if ($LASTEXITCODE -ne 0) { throw 'Nginx configuration check failed' }
$api = $null
$web = $null
try {
    $api = Start-Process -FilePath $java -ArgumentList @('-Xms256m','-Xmx1024m','-jar','app/och-api.jar','--spring.config.location=file:./config/application.yml') -WorkingDirectory $DemoRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $DemoRoot 'logs/api.log') -RedirectStandardError (Join-Path $DemoRoot 'logs/api-error.log') -PassThru
    @{pid=$api.Id; started=$api.StartTime.ToUniversalTime().ToString('o'); executable=$java} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $DemoRoot 'runtime/api-process.json')
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($api.HasExited) { throw 'API exited; inspect logs/api.log and logs/api-error.log' }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($env:OCH_API_PORT)/v3/api-docs" -TimeoutSec 15
            if ($response.StatusCode -eq 200 -and ($response.Content | ConvertFrom-Json).openapi) { $ready=$true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'API readiness timed out; inspect logs/api.log' }
    $web = Start-Process -FilePath $nginx -ArgumentList @('-p', ('"' + $prefix + '"'), '-c', 'config/nginx.conf') -WorkingDirectory $DemoRoot -WindowStyle Hidden -PassThru
    Start-Sleep -Seconds 1
    if ($web.HasExited) { throw 'Nginx exited; inspect logs/nginx-error.log' }
    $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($env:OCH_WEB_PORT)/healthz" -TimeoutSec 5
    if ($health.StatusCode -ne 200) { throw 'Web readiness check failed' }
    Write-Output "Demo ready: http://$($env:OCH_PUBLIC_HOST):$($env:OCH_WEB_PORT)/login"
    Write-Output 'Login: admin / the password you chose during initialization'
} catch {
    if ($web -and -not $web.HasExited) {
        & $nginx -p $prefix -c config/nginx.conf -s quit
    }
    if ($api -and -not $api.HasExited) { Stop-Process -Id $api.Id }
    Remove-Item -LiteralPath (Join-Path $DemoRoot 'runtime/api-process.json') -ErrorAction SilentlyContinue
    throw
}
