# Starts the local PostgreSQL (if it is not running) and the backend.
#   .\run.ps1                 run on port 5080, all interfaces (reachable from the Android emulator as 10.0.2.2:5080)
#   .\run.ps1 -Test           run the test suite instead
#   .\run.ps1 -Background     start the backend detached, log to backend.log
# Secrets are read from %LOCALAPPDATA%\ggpower-dev\secrets.env (see .env.example); nothing secret lives in the repo.
param([switch]$Test, [switch]$Background, [switch]$Stop, [int]$Port = 5080)

$ErrorActionPreference = 'Stop'
$dev = "$env:LOCALAPPDATA\ggpower-dev"
$env:DOTNET_ROOT = "$dev\dotnet"
$env:PATH = "$dev\dotnet;$env:PATH"
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$env:DOTNET_NOLOGO = '1'

function Stop-Backend {
    Get-CimInstance Win32_Process -Filter "Name = 'GgPower.Api.exe' OR Name = 'dotnet.exe'" |
        Where-Object { $_.Name -eq 'GgPower.Api.exe' -or $_.CommandLine -like '*GgPower.Api*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
if ($Stop) { Stop-Backend; 'Backend stopped.'; exit 0 }

if (-not (Test-Path "$dev\secrets.env")) { throw "Missing $dev\secrets.env (see .env.example)" }
$s = @{}
Get-Content "$dev\secrets.env" | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; $s[$k.Trim()] = $v.Trim() }

& "$dev\pgsql\bin\pg_isready.exe" -h localhost -p 5433 -q
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Starting PostgreSQL...'
    Start-Process -FilePath "$dev\pgsql\bin\pg_ctl.exe" -ArgumentList '-D', "`"$dev\pgdata`"", '-l', "`"$dev\pg.log`"", 'start' -WindowStyle Hidden
    foreach ($i in 1..30) { Start-Sleep -Milliseconds 500; & "$dev\pgsql\bin\pg_isready.exe" -h localhost -p 5433 -q; if ($LASTEXITCODE -eq 0) { break } }
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL did not start; see ' + "$dev\pg.log" }
}

$db = if ($Test) { 'ggpower_test' } else { 'ggpower' }
$env:GGP_ConnectionStrings__Main = "Host=localhost;Port=5433;Database=$db;Username=ggpower;Password=$($s.PG_APP_PASSWORD);Maximum Pool Size=40"
$env:GGP_Security__JwtSigningKey = $s.JWT_SIGNING_KEY

if ($Test) {
    dotnet test "$PSScriptRoot\tests\GgPower.Tests" --nologo
    exit $LASTEXITCODE
}

$env:GGP_Mqtt__Host = $s.MQTT_HOST
$env:GGP_Mqtt__Port = if ($s.MQTT_PORT) { $s.MQTT_PORT } else { '8883' }
$env:GGP_Mqtt__Username = $s.MQTT_USER
$env:GGP_Mqtt__Password = $s.MQTT_PASSWORD
$i = 0
foreach ($t in (($s.MQTT_TENANTS, 'ggp' -ne $null)[0] -split ',')) { Set-Item "env:GGP_Ingest__Tenants__$i" $t.Trim(); $i++ }
# Development conveniences: one-time codes are written to the log because no SMS provider is configured.
$env:GGP_Security__LogOtpCodes = 'true'
$env:ASPNETCORE_ENVIRONMENT = 'Development'
$env:ASPNETCORE_URLS = "http://*:$Port"    # IPv4 and IPv6, so "localhost" connects without a fallback delay

if ($Background) {
    Stop-Backend
    $p = Start-Process -FilePath "$dev\dotnet\dotnet.exe" -ArgumentList 'run', '--project', "`"$PSScriptRoot\src\GgPower.Api`"", '--no-launch-profile', '-c', 'Release' `
        -RedirectStandardOutput "$PSScriptRoot\backend.log" -RedirectStandardError "$PSScriptRoot\backend.err.log" -WindowStyle Hidden -PassThru
    "Backend starting (pid $($p.Id)), log: $PSScriptRoot\backend.log"
} else {
    dotnet run --project "$PSScriptRoot\src\GgPower.Api" --no-launch-profile -c Release
}
