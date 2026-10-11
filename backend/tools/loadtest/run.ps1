# One load-test run (step A0): re-seed history for N devices, run LoadGen for M minutes, sample CPU every 30 s,
# then print a summary line for backend/docs/LOADTEST.md. The backend must be running (run-backend.ps1).
#   .\run.ps1 -Devices 2000 -Minutes 6 [-BackendLog <file>]
param([int]$Devices = 1000, [double]$Minutes = 6, [string]$BackendLog = "$PSScriptRoot\backend-load.log", [string]$Api = 'http://localhost:5090')
$ErrorActionPreference = 'Stop'
$repo = Resolve-Path "$PSScriptRoot\..\.."
function Sql($q) { (docker exec ggpower-pg-test psql -U ggpower -d ggpower_load -At -c $q) }

Write-Host "Seeding history for $Devices devices..."
Get-Content "$PSScriptRoot\seed.sql" -Raw | docker exec -i ggpower-pg-test psql -U ggpower -d ggpower_load -q -v n=$Devices | Out-Null
$rawBefore = [long](Sql "SELECT pg_total_relation_size('telemetry_raw') + coalesce((SELECT sum(pg_total_relation_size(c.oid)) FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid WHERE i.inhparent = 'telemetry_raw'::regclass), 0)")
$rowsBefore = [long](Sql "SELECT count(*) FROM telemetry_raw WHERE serial LIKE 'LOAD-%'")
$logStart = (Get-Content $BackendLog).Count
$healthBefore = Invoke-RestMethod "$Api/healthz"

$env:DOTNET_ROLL_FORWARD = 'Major'
$gen = Start-Process dotnet -ArgumentList "run --project `"$repo\tools\GgPower.LoadGen`" -c Release -- --devices $Devices --minutes $Minutes --broker localhost:1884 --api $Api" `
    -RedirectStandardOutput "$PSScriptRoot\loadgen-$Devices.log" -NoNewWindow -PassThru

$apiProc = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'dotnet.exe' -and $_.CommandLine -match 'GgPower\.Api\.dll' } | Select-Object -First 1
if (-not $apiProc) { $gen | Stop-Process -Force; throw 'Backend process not found: start run-backend.ps1 first' }
$proc = Get-Process -Id $apiProc.ProcessId
$cores = [Environment]::ProcessorCount
$apiCpu = @(); $pgCpu = @(); $mem = @()
Start-Sleep -Seconds 40   # build + connect + status burst
while (-not $gen.HasExited) {
    $t0 = $proc.TotalProcessorTime; Start-Sleep -Seconds 30; $proc.Refresh()
    $apiCpu += ($proc.TotalProcessorTime - $t0).TotalSeconds / 30 * 100      # % of one core
    $pgCpu += [double]((docker stats ggpower-pg-test --no-stream --format '{{.CPUPerc}}') -replace '%', '')
    $mem += $proc.WorkingSet64 / 1MB
}
$healthAfter = Invoke-RestMethod "$Api/healthz"
$rawAfter = [long](Sql "SELECT pg_total_relation_size('telemetry_raw') + coalesce((SELECT sum(pg_total_relation_size(c.oid)) FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid WHERE i.inhparent = 'telemetry_raw'::regclass), 0)")
$rowsAfter = [long](Sql "SELECT count(*) FROM telemetry_raw WHERE serial LIKE 'LOAD-%'")
$rollups = Get-Content $BackendLog | Select-Object -Skip $logStart | Select-String 'rollup took ([\d.,]+) ms' | ForEach-Object { [double]($_.Matches[0].Groups[1].Value -replace ',', '.') }

$received = $healthAfter.messages_received - $healthBefore.messages_received
$seconds = $Minutes * 60
# Storage per row of the samples this run wrote: data + 28 B tuple header / line pointer + ~40 B primary-key entry (serial, ts).
$bytesPerRow = [double](Sql "SELECT coalesce(avg(pg_column_size(t.*)), 0) + 68 FROM telemetry_raw t WHERE serial LIKE 'LOAD-%' AND ts > now() - interval '$([int]$Minutes) minutes'")
$summary = [pscustomobject]@{
    devices = $Devices
    minutes = $Minutes
    received_per_s = [math]::Round($received / $seconds, 1)
    expected_per_s = [math]::Round($Devices * 0.25, 1)
    dropped = $healthAfter.rows_dropped - $healthBefore.rows_dropped
    last_batch_ms = $healthAfter.last_batch_ms
    rollup_ms_avg = if ($rollups) { [math]::Round(($rollups | Measure-Object -Average).Average) } else { $null }
    rollup_ms_max = if ($rollups) { [math]::Round(($rollups | Measure-Object -Maximum).Maximum) } else { $null }
    api_cpu_pct_of_core_avg = if ($apiCpu) { [math]::Round(($apiCpu | Measure-Object -Average).Average, 1) } else { $null }
    api_mem_mb_max = if ($mem) { [math]::Round(($mem | Measure-Object -Maximum).Maximum) } else { $null }
    pg_cpu_pct_avg = if ($pgCpu) { [math]::Round(($pgCpu | Measure-Object -Average).Average, 1) } else { $null }
    pg_cpu_pct_max = if ($pgCpu) { [math]::Round(($pgCpu | Measure-Object -Maximum).Maximum, 1) } else { $null }
    raw_bytes_per_row = [math]::Round($bytesPerRow)
    raw_mb_per_device_day = [math]::Round($bytesPerRow * 17280 / 1MB, 2)
    cores = $cores
}
$summary | Format-List
$summary | ConvertTo-Json -Compress | Add-Content "$PSScriptRoot\results.jsonl"




