$ErrorActionPreference = 'Stop'
$script:DemoRoot = Split-Path -Parent $PSScriptRoot
$script:Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-DemoSetting([string]$Name, [string]$Fallback) {
    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value)) { return $Fallback }
    return $value
}

function Initialize-DemoEnvironment {
    $localEnv = Join-Path $DemoRoot 'config/demo.env'
    if (Test-Path -LiteralPath $localEnv) {
        foreach ($line in [IO.File]::ReadAllLines($localEnv)) {
            if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }
            if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid config/demo.env line; use KEY=value without shell commands' }
            if (-not [Environment]::GetEnvironmentVariable($Matches[1], 'Process')) {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
            }
        }
    }
    $defaults = @{
        OCH_DB_HOST='127.0.0.1'; OCH_DB_PORT='3306'; OCH_DB_NAME='xhbt_demo'; OCH_DB_USER='xhbt_demo'
        OCH_REDIS_HOST='127.0.0.1'; OCH_REDIS_PORT='6379'; OCH_REDIS_DATABASE='10'
        OCH_API_PORT='14320'; OCH_FILE_PORT='19527'; OCH_WEB_PORT='8088'
        OCH_WEB_BIND_ADDRESS='127.0.0.1'; OCH_PUBLIC_HOST='127.0.0.1'
    }
    foreach ($name in $defaults.Keys) {
        [Environment]::SetEnvironmentVariable($name, (Get-DemoSetting $name $defaults[$name]), 'Process')
    }
    foreach ($name in @('OCH_DB_PORT','OCH_REDIS_PORT','OCH_API_PORT','OCH_FILE_PORT','OCH_WEB_PORT')) {
        $value = Get-DemoSetting $name ''
        if ($value -notmatch '^\d+$' -or [int]$value -lt 1 -or [int]$value -gt 65535) { throw "Invalid port: $name" }
    }
    if ($env:OCH_DB_NAME -notmatch '^[A-Za-z][A-Za-z0-9_]*$') { throw 'OCH_DB_NAME must be a simple database identifier' }
    if ($env:OCH_WEB_BIND_ADDRESS -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { throw 'OCH_WEB_BIND_ADDRESS must be an IPv4 address' }
    $env:OCH_DB_URL = Get-DemoSetting 'OCH_DB_URL' "jdbc:mysql://$($env:OCH_DB_HOST):$($env:OCH_DB_PORT)/$($env:OCH_DB_NAME)?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    $env:OCH_BIND_ADDRESS = '127.0.0.1'
    $env:OCH_ASR_PROVIDER = 'MOCK'
    $env:OCH_AUDIO_STREAM_ENABLED = 'false'
    foreach ($directory in @('logs','runtime','temp','runtime/public/files','runtime/public/recordings')) {
        New-Item -ItemType Directory -Path (Join-Path $DemoRoot $directory) -Force | Out-Null
    }
}

function Resolve-DemoJava {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    } else { $java = (Get-Command java -ErrorAction Stop).Source }
    $version = (& $java --version | Out-String)
    if ($version -notmatch '(?:openjdk|java) (\d+)' -or [int]$Matches[1] -lt 17) { throw 'JDK 17 or newer is required' }
    return $java
}

function Resolve-DemoNginx {
    if ($env:OCH_NGINX_EXE) { return (Resolve-Path -LiteralPath $env:OCH_NGINX_EXE).Path }
    $bundled = Join-Path $DemoRoot 'bin/nginx/nginx.exe'
    if (Test-Path $bundled) { return $bundled }
    return (Get-Command nginx -ErrorAction Stop).Source
}

function Read-DemoSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    return [System.Net.NetworkCredential]::new('', $secure).Password
}

function Test-DemoPort([string]$HostName, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(2000)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch { return $false } finally { $client.Dispose() }
}

function Write-DemoNginxConfig {
    $text = [IO.File]::ReadAllText((Join-Path $DemoRoot 'config/nginx.conf.template'))
    $text = $text.Replace('__WEB_BIND__', $env:OCH_WEB_BIND_ADDRESS).Replace('__WEB_PORT__', $env:OCH_WEB_PORT).Replace('__API_PORT__', $env:OCH_API_PORT)
    [IO.File]::WriteAllText((Join-Path $DemoRoot 'config/nginx.conf'), $text, $Utf8NoBom)
}
