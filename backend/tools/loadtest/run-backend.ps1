# Runs the Release build of the backend against the load-test broker and database (step A0).
#   docker run -d --name ggpower-mqtt-load -p 1884:1883 -v <repo>\backend\tools\loadtest\mosquitto.conf:/mosquitto/config/mosquitto.conf eclipse-mosquitto:2
#   docker exec ggpower-pg-test psql -U ggpower -d ggpower_test -c "CREATE DATABASE ggpower_load;"
#   dotnet build backend\src\GgPower.Api -c Release
#   .\run-backend.ps1 [-Log <file>]
param([string]$Log = "$PSScriptRoot\backend-load.log")
Get-Content "$PSScriptRoot\backend.env" | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
}
$dll = Join-Path $PSScriptRoot '..\..\src\GgPower.Api\bin\Release\net9.0\GgPower.Api.dll'
& dotnet $dll *> $Log
