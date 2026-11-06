. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-DemoEnvironment
$java = Resolve-DemoJava
$expected = "INITIALIZE $($env:OCH_DB_NAME)"
Write-Output "Initialize an EMPTY MySQL database at $($env:OCH_DB_HOST):$($env:OCH_DB_PORT)/$($env:OCH_DB_NAME)."
if ((Read-Host "Type '$expected' to continue") -cne $expected) { throw 'Initialization cancelled' }
if (-not $env:OCH_DB_PASSWORD) { $env:OCH_DB_PASSWORD = Read-DemoSecret 'MySQL demo database password' }
$password = Read-DemoSecret 'Choose an admin password (at least 12 characters)'
if ($password.Length -lt 12) { throw 'Administrator password must have at least 12 characters' }
if ($password -cne (Read-DemoSecret 'Confirm admin password')) { throw 'Administrator passwords do not match' }
try {
    $env:OCH_BOOTSTRAP_PASSWORD = $password
    $classpath = (Join-Path $DemoRoot 'tools/mysql-connector-j.jar') + [IO.Path]::PathSeparator + (Join-Path $DemoRoot 'tools/spring-security-crypto.jar') + [IO.Path]::PathSeparator + (Join-Path $DemoRoot 'tools/spring-jcl.jar')
    & $java --class-path $classpath (Join-Path $DemoRoot 'tools/DemoDatabase.java') (Join-Path $DemoRoot 'database/01-schema.sql') (Join-Path $DemoRoot 'database/02-seed.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Initialization failed. MySQL DDL cannot roll back; inspect the error and use a new empty database, not a business database.' }
} finally {
    Remove-Item Env:OCH_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    $password=$null
}
Write-Output 'Database initialized. Login as admin with the password you just chose.'
