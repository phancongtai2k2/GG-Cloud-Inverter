# Plan B7: load test of two instances behind Caddy (docker-compose.multi.yml must be up), with instance 2 stopped
# and started again during the run while an "app" keeps reading through Caddy. Prints a summary for docs/LOADTEST.md.
#   .\run-multi.ps1 -Devices 5000 -Minutes 6 [-StopAt 120] [-StartAt 180]
param([int]$Devices = 5000, [double]$Minutes = 6, [int]$StopAt = 120, [int]$StartAt = 180)
$ErrorActionPreference = 'Stop'
$repo = Resolve-Path "$PSScriptRoot\..\.."
$site = 'http://localhost:5091'      # Caddy
$direct = 'http://localhost:5092'    # instance 1 (ingest, jobs)
function Sql($q) { (docker exec ggpower-pg-test psql -U ggpower -d ggpower_load -At -c $q) }

Write-Host "Seeding history for $Devices devices..."
Get-Content "$PSScriptRoot\seed.sql" -Raw | docker exec -i ggpower-pg-test psql -U ggpower -d ggpower_load -q -v n=$Devices | Out-Null

# An app user with one project on a load device, made through Caddy like the app does.
$n = Get-Random -Minimum 10000000 -Maximum 99999999
$phone = "09$n"
Invoke-RestMethod -Method Post "$site/api/v1/auth/register" -ContentType 'application/json' `
    -Body (@{ name = 'Load'; email = "load$n@test.example"; phone = $phone; password = 'matkhau123' } | ConvertTo-Json) | Out-Null
Start-Sleep -Seconds 1
$code = $(docker logs ggpower-multi-api1-1 2>&1; docker logs ggpower-multi-api2-1 2>&1) | Select-String "DEV OTP for $phone \(\w+\): (\d+)" | Select-Object -Last 1 | ForEach-Object { $_.Matches[0].Groups[1].Value }
$tokens = Invoke-RestMethod -Method Post "$site/api/v1/auth/register/verify" -ContentType 'application/json' -Body (@{ phone = $phone; code = $code } | ConvertTo-Json)
$auth = @{ Authorization = "Bearer $($tokens.access_token)" }
$serial = Sql "SELECT d.serial FROM devices d WHERE d.serial LIKE 'LOAD-%' AND NOT EXISTS (SELECT 1 FROM project_devices p WHERE p.serial = d.serial) AND d.serial <= 'LOAD-' || lpad('$Devices', 5, '0') ORDER BY random() LIMIT 1"
$project = (Invoke-RestMethod -Method Post "$site/api/v1/projects" -Headers $auth -ContentType 'application/json' -Body (@{
    serial = $serial; name = "Load $serial"; site_type = 'home'; address = 'x'; time_zone = 'Asia/Ho_Chi_Minh'; installed_on = '2026-01-01'; pv_kwp = 5 } | ConvertTo-Json)).project_id
Write-Host "App user $phone, project $project on $serial"

$logStart = (docker logs ggpower-multi-api1-1 2>&1 | Measure-Object -Line).Lines
$healthBefore = Invoke-RestMethod "$direct/healthz"
$env:DOTNET_ROLL_FORWARD = 'Major'
$gen = Start-Process dotnet -ArgumentList "run --project `"$repo\tools\GgPower.LoadGen`" -c Release -- --devices $Devices --minutes $Minutes --broker localhost:1884 --api $direct" `
    -RedirectStandardOutput "$PSScriptRoot\loadgen-multi-$Devices.log" -NoNewWindow -PassThru

# The app: reads the overview through Caddy every 200 ms for the whole run.
$probe = Start-Job -ArgumentList $site, $project, $tokens.access_token, ($Minutes * 60 + 40) -ScriptBlock {
    param($site, $project, $token, $seconds)
    $until = (Get-Date).AddSeconds($seconds)
    $h = @{ Authorization = "Bearer $token" }
    while ((Get-Date) -lt $until) {
        $t0 = Get-Date
        try {
            $r = Invoke-WebRequest -UseBasicParsing "$site/api/v1/projects/$project/overview" -Headers $h -TimeoutSec 10
            $status = [int]$r.StatusCode
            $pv = ($r.Content | ConvertFrom-Json).energy.pv_kw
        } catch { $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }; $pv = $null }
        [pscustomobject]@{ at = $t0; status = $status; ms = ((Get-Date) - $t0).TotalMilliseconds; pv = $pv }
        Start-Sleep -Milliseconds 200
    }
}

$cpu = @{ api1 = @(); api2 = @(); pg = @(); redis = @() }
$names = @{ api1 = 'ggpower-multi-api1-1'; api2 = 'ggpower-multi-api2-1'; pg = 'ggpower-pg-test'; redis = 'ggpower-redis-test' }
$started = Get-Date
$stopped = $null; $restarted = $null
while (-not $gen.HasExited) {
    Start-Sleep -Seconds 10
    $elapsed = ((Get-Date) - $started).TotalSeconds
    if (-not $stopped -and $elapsed -ge $StopAt) { Write-Host "stopping api2 at $([int]$elapsed) s"; $stopped = Get-Date; docker stop ggpower-multi-api2-1 | Out-Null }
    if ($stopped -and -not $restarted -and $elapsed -ge $StartAt) { Write-Host "starting api2 at $([int]$elapsed) s"; $restarted = Get-Date; docker start ggpower-multi-api2-1 | Out-Null }
    if ($elapsed -ge 40) {
        foreach ($k in $names.Keys) {
            $v = docker stats $names[$k] --no-stream --format '{{.CPUPerc}}' 2>$null
            if ($v) { $cpu[$k] += [double]($v -replace '%', '') }
        }
    }
}
$calls = Receive-Job $probe -Wait -AutoRemoveJob
$healthAfter = Invoke-RestMethod "$direct/healthz"
$rollups = docker logs ggpower-multi-api1-1 2>&1 | Select-Object -Skip $logStart | Select-String 'rollup took ([\d.,]+) ms' | ForEach-Object { [double]($_.Matches[0].Groups[1].Value -replace ',', '.') }
$failed = @($calls | Where-Object { $_.status -ne 200 })
$aroundStop = @($calls | Where-Object { $stopped -and $_.at -ge $stopped.AddSeconds(-5) -and $_.at -le $restarted.AddSeconds(30) })
function Avg($a) { if ($a.Count) { [math]::Round(($a | Measure-Object -Average).Average, 1) } else { $null } }
$summary = [pscustomobject]@{
    devices = $Devices
    minutes = $Minutes
    received_per_s = [math]::Round(($healthAfter.messages_received - $healthBefore.messages_received) / ($Minutes * 60), 1)
    expected_per_s = [math]::Round($Devices * 0.25, 1)
    dropped = $healthAfter.rows_dropped - $healthBefore.rows_dropped
    rollup_ms_avg = if ($rollups) { [math]::Round(($rollups | Measure-Object -Average).Average) } else { $null }
    rollup_ms_max = if ($rollups) { [math]::Round(($rollups | Measure-Object -Maximum).Maximum) } else { $null }
    cpu_api1_pct = Avg $cpu.api1
    cpu_api2_pct = Avg $cpu.api2
    cpu_pg_pct = Avg $cpu.pg
    cpu_redis_pct = Avg $cpu.redis
    app_calls = $calls.Count
    app_failed = $failed.Count
    app_failed_statuses = ($failed | Group-Object status | ForEach-Object { "$($_.Name)x$($_.Count)" }) -join ','
    app_calls_around_stop = $aroundStop.Count
    app_ms_p50 = ($calls | Sort-Object ms | Select-Object -Index ([int]($calls.Count * 0.5))).ms -as [int]
    app_ms_p99 = ($calls | Sort-Object ms | Select-Object -Index ([int]($calls.Count * 0.99))).ms -as [int]
    app_ms_max = (($calls | Measure-Object ms -Maximum).Maximum) -as [int]
    app_saw_live_pv = @($calls | Where-Object { $_.pv -ne $null }).Count
}
$summary | Format-List
$summary | ConvertTo-Json -Compress | Add-Content "$PSScriptRoot\results.jsonl"
