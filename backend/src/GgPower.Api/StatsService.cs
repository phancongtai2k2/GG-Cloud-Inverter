using Dapper;
using GgPower.Infrastructure;
using Microsoft.Extensions.Caching.Memory;
using Microsoft.Extensions.Options;

namespace GgPower.Api;

public sealed class StatsOptions
{
    /// <summary>Grid emission factor used for "CO₂ avoided" (kg per kWh of PV yield).</summary>
    public double Co2KgPerKwh { get; set; } = 0.6766;
    /// <summary>Price used for savings when the project uses the tiered EVN tariff (tier prices are not agreed yet).</summary>
    public long DefaultPricePerKwh { get; set; } = 2500;
    public int MaxProjectsPerUser { get; set; } = 20;
}

public sealed class ContentOptions
{
    public string Hotline { get; set; } = "1900 6868";
    public string SupportEmail { get; set; } = "support@ggpower.vn";
    public string? ExploreFile { get; set; }
    public int InverterWarrantyYears { get; set; } = 5;
}

/// <summary>Statistics read from the roll-up tables. Closed periods never change and are cached.</summary>
public sealed class StatsService(Db db, IMemoryCache cache, ICacheVersions versions, IOptions<StatsOptions> options, TimeProvider clock)
{
    // A period counts as closed (never changing again) one day after it ended: until then a datalogger
    // that was cut off around midnight may still deliver the samples it stored.
    private static bool Settled(DateOnly lastDay, DateOnly today) => lastDay < today.AddDays(-1);

    private readonly StatsOptions _o = options.Value;

    private sealed class Bucket
    {
        public int Idx { get; set; }
        public double PvKwh { get; set; }
        public double LoadKwh { get; set; }
        public double ImportKwh { get; set; }
        public double ExportKwh { get; set; }
        public double ChargeKwh { get; set; }
        public double DischargeKwh { get; set; }
        public double PvSelfKwh { get; set; }
        public double PvChargeKwh { get; set; }
        public double PvExportKwh { get; set; }
        public double? Soc { get; set; }
    }

    private const string Sums =
        "sum(pv_kwh) AS pv_kwh, sum(load_kwh) AS load_kwh, sum(import_kwh) AS import_kwh, sum(export_kwh) AS export_kwh, sum(charge_kwh) AS charge_kwh, sum(discharge_kwh) AS discharge_kwh, sum(pv_self_kwh) AS pv_self_kwh, sum(pv_charge_kwh) AS pv_charge_kwh, sum(pv_export_kwh) AS pv_export_kwh";

    public static DateTimeOffset LocalMidnightUtc(DateOnly day, TimeZoneInfo zone) =>
        new DateTimeOffset(TimeZoneInfo.ConvertTimeToUtc(day.ToDateTime(TimeOnly.MinValue, DateTimeKind.Unspecified), zone), TimeSpan.Zero);

    public DateOnly Today(TimeZoneInfo zone) => DateOnly.FromDateTime(TimeZoneInfo.ConvertTime(clock.GetUtcNow(), zone).DateTime);

    /// <summary>Returns the response body and whether the period is closed (immutable).</summary>
    public async Task<(object Body, bool Closed)> StatsAsync(ProjectAccess a, string period, DateOnly anchor, CancellationToken ct)
    {
        var serial = a.PrimarySerial;
        var today = Today(a.Zone);
        int count;
        bool closed;
        switch (period)
        {
            case "day": count = 12; closed = Settled(anchor, today); break;
            case "month": count = DateTime.DaysInMonth(anchor.Year, anchor.Month); closed = Settled(new DateOnly(anchor.Year, anchor.Month, count), today); break;
            default: count = 12; closed = Settled(new DateOnly(anchor.Year, 12, 31), today); break;
        }
        // The data version changes when stored samples were added to the past, which drops older cache entries.
        var key = $"stats:{serial}:{await versions.VersionAsync(serial)}:{period}:{anchor:yyyyMMdd}";
        if (closed && cache.TryGetValue(key, out object? hit)) return (hit!, true);

        var rows = new Bucket[count];
        for (var i = 0; i < count; i++) rows[i] = new Bucket { Idx = i };
        if (serial is not null)
        {
            await using var c = await db.OpenAsync(ct);
            IEnumerable<Bucket> found;
            if (period == "day")
            {
                var from = LocalMidnightUtc(anchor, a.Zone);
                found = await c.QueryAsync<Bucket>(
                    $"SELECT (floor(extract(epoch FROM bucket - @from) / 7200))::int AS idx, {Sums}, avg(soc) AS soc FROM rollup_15m WHERE serial = @serial AND bucket >= @from AND bucket < @to GROUP BY 1",
                    new { serial, from, to = LocalMidnightUtc(anchor.AddDays(1), a.Zone) });
            }
            else if (period == "month")
            {
                var first = new DateOnly(anchor.Year, anchor.Month, 1);
                found = await c.QueryAsync<Bucket>(
                    $"SELECT (day - @first)::int AS idx, {Sums}, avg(soc_avg) AS soc FROM rollup_1d WHERE serial = @serial AND day >= @first AND day < @next GROUP BY 1",
                    new { serial, first, next = first.AddMonths(1) });
            }
            else
            {
                var first = new DateOnly(anchor.Year, 1, 1);
                found = await c.QueryAsync<Bucket>(
                    $"SELECT (extract(month FROM day))::int - 1 AS idx, {Sums}, avg(soc_avg) AS soc FROM rollup_1d WHERE serial = @serial AND day >= @first AND day < @next GROUP BY 1",
                    new { serial, first, next = first.AddYears(1) });
            }
            foreach (var b in found) if (b.Idx >= 0 && b.Idx < count) rows[b.Idx] = b;
        }

        static double R(double v) => Math.Round(v, 3);
        var pv = rows.Sum(r => r.PvKwh);
        var load = rows.Sum(r => r.LoadKwh);
        var import = rows.Sum(r => r.ImportKwh);
        var export = rows.Sum(r => r.ExportKwh);
        var price = a.Project.TariffKind == "fixed" && a.Project.TariffFixed is { } f ? f : _o.DefaultPricePerKwh;
        var body = new
        {
            period, anchor = anchor.ToString("yyyy-MM-dd"), closed,
            energy_buckets = rows.Select(r => new
            {
                pv_kwh = R(r.PvKwh), grid_net_kwh = R(r.ImportKwh - r.ExportKwh), battery_charge_kwh = R(r.ChargeKwh),
                battery_discharge_kwh = R(r.DischargeKwh), load_kwh = R(r.LoadKwh), average_soc_percent = r.Soc is { } s ? (int?)Math.Round(s) : null,
            }),
            yield_buckets = rows.Select(r => new { self_kwh = R(r.PvSelfKwh), charge_kwh = R(r.PvChargeKwh), export_kwh = R(r.PvExportKwh) }),
            summary = new
            {
                consumption_kwh = R(load),
                self_use_percent = pv > 0 ? (int)Math.Round(Math.Clamp((pv - export) / pv, 0, 1) * 100) : 0,
                // Energy the home used without buying it, at the purchase price, plus what was sold.
                saving_vnd = (long)Math.Round(Math.Max(load - import, 0) * price + export * a.Project.TariffSell),
                co2_kg = Math.Round(pv * _o.Co2KgPerKwh, 1),
                price_per_kwh = price,
                price_is_default = a.Project.TariffKind != "fixed",
            },
        };
        if (closed) cache.Set(key, (object)body, TimeSpan.FromHours(6));
        return (body, closed);
    }

    public async Task<(object Body, bool Closed)> DayCurvesAsync(ProjectAccess a, DateOnly date, CancellationToken ct)
    {
        var serial = a.PrimarySerial;
        var closed = Settled(date, Today(a.Zone));
        var key = $"curves:{serial}:{await versions.VersionAsync(serial)}:{date:yyyyMMdd}";
        if (closed && cache.TryGetValue(key, out object? hit)) return (hit!, true);
        var from = LocalMidnightUtc(date, a.Zone);
        IEnumerable<dynamic> rows = [];
        if (serial is not null)
        {
            await using var c = await db.OpenAsync(ct);
            rows = await c.QueryAsync(
                "SELECT (extract(epoch FROM bucket - @from) / 60)::int AS minute, pv_w, grid_w, batt_w, load_w, soc, batt_a FROM rollup_15m WHERE serial = @serial AND bucket >= @from AND bucket < @to ORDER BY bucket",
                new { serial, from, to = LocalMidnightUtc(date.AddDays(1), a.Zone) });
        }
        static double? Kw(object? w) => w is null ? null : Math.Round(Convert.ToDouble(w) / 1000, 3);
        var list = rows.ToList();
        var body = new
        {
            date = date.ToString("yyyy-MM-dd"), step_minutes = 15, closed,
            power = list.Select(r => new { minute = (int)r.minute, pv_kw = Kw(r.pv_w), grid_kw = Kw(r.grid_w), battery_kw = Kw(r.batt_w), load_kw = Kw(r.load_w) }),
            battery = list.Where(r => r.soc is not null || r.batt_a is not null).Select(r => new
            {
                minute = (int)r.minute,
                current_a = r.batt_a is null ? (double?)null : Math.Round(Convert.ToDouble(r.batt_a), 1),
                soc_percent = r.soc is null ? (int?)null : (int)Math.Round(Convert.ToDouble(r.soc)),
            }),
        };
        if (closed) cache.Set(key, (object)body, TimeSpan.FromHours(6));
        return (body, closed);
    }

    public async Task<double> DayYieldAsync(string serial, DateOnly day, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        return await c.ExecuteScalarAsync<double?>("SELECT pv_kwh FROM rollup_1d WHERE serial = @serial AND day = @day", new { serial, day }) ?? 0;
    }

    /// <summary>kWh charged into the battery on a local day: the PV part and the rest (from the grid).</summary>
    public async Task<ChargedToday> DayChargeAsync(string serial, DateOnly day, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var r = await c.QuerySingleOrDefaultAsync<(double Pv, double Total)?>(
            "SELECT pv_charge_kwh, charge_kwh FROM rollup_1d WHERE serial = @serial AND day = @day", new { serial, day });
        return r is { } v ? new ChargedToday(v.Pv, Math.Max(v.Total - v.Pv, 0)) : new ChargedToday(0, 0);
    }

    /// <summary>Average load of the last 7 days in kW (for the backup-hours estimate).</summary>
    public async Task<double?> AverageLoadKwAsync(string serial, CancellationToken ct)
    {
        var key = $"avgload:{serial}";
        if (cache.TryGetValue(key, out double? hit)) return hit;
        await using var c = await db.OpenAsync(ct);
        var w = await c.ExecuteScalarAsync<double?>("SELECT avg(load_w) FROM rollup_15m WHERE serial = @serial AND bucket >= now() - interval '7 days'", new { serial });
        double? kw = w is { } v ? Math.Round(v / 1000, 2) : null;
        cache.Set(key, kw, TimeSpan.FromMinutes(15));
        return kw;
    }

    // History fields the app can chart, mapped to roll-up / raw columns.
    private static readonly Dictionary<string, (string Device, string Rollup, string Raw, string RawTable)> Fields = new()
    {
        ["pv_power"] = ("inverter", "pv_w", "pv_w", "telemetry_raw"),
        ["load_power"] = ("inverter", "load_w", "load_w", "telemetry_raw"),
        ["temp_dcac"] = ("inverter", "inv_temp", "inv_temp", "telemetry_raw"),
        ["grid_voltage"] = ("inverter", "grid_v", "grid_v", "telemetry_raw"),
        ["grid_frequency"] = ("inverter", "grid_hz", "grid_hz", "telemetry_raw"),
        ["soc"] = ("battery", "soc", "soc", "battery_raw"),
        ["pack_current"] = ("battery", "batt_a", "current_a", "battery_raw"),
        ["battery_temp"] = ("battery", "batt_temp", "temp_cells", "battery_raw"),
        // cell_delta needs per-cell data the datalogger does not publish: always absent.
    };

    public static bool KnownField(string id) => Fields.ContainsKey(id) || id == "cell_delta";

    /// <summary>hour: 1-minute steps over 60 min; day: 15-minute steps over 24 h; week: hourly steps over 7 days. Ends at "now".</summary>
    public async Task<object> HistoryAsync(ProjectAccess a, string range, string[] fields, CancellationToken ct)
    {
        var (step, span) = range switch { "hour" => (1, 60), "week" => (60, 7 * 24 * 60), _ => (15, 24 * 60) };
        var now = clock.GetUtcNow();
        var endTicks = now.UtcDateTime.Ticks - now.UtcDateTime.Ticks % TimeSpan.FromMinutes(step).Ticks;
        var end = new DateTimeOffset(endTicks, TimeSpan.Zero);
        var start = end.AddMinutes(-span);
        var size = span / step + 1;
        var values = new Dictionary<string, double?[]>();
        var serial = a.PrimarySerial;
        await using var c = serial is null ? null : await db.OpenAsync(ct);
        foreach (var id in fields.Distinct())
        {
            var arr = new double?[size];
            values[id] = arr;
            if (c is null || !Fields.TryGetValue(id, out var f)) continue;
            // Column and table names come from the fixed map above, never from the request.
            var sql = range == "hour"
                ? $"SELECT (floor(extract(epoch FROM ts - @start) / 60))::int AS idx, avg({f.Raw}) AS v FROM {f.RawTable} WHERE serial = @serial AND ts >= @start AND ts <= @to GROUP BY 1"
                : $"SELECT (floor(extract(epoch FROM bucket - @start) / (@step * 60)))::int AS idx, avg({f.Rollup}) AS v FROM rollup_15m WHERE serial = @serial AND bucket >= @start AND bucket <= @to GROUP BY 1";
            foreach (var r in await c.QueryAsync<(int Idx, double? V)>(sql, new { serial, start, to = end.AddMinutes(step), step }))
                if (r.Idx >= 0 && r.Idx < size && r.V is { } v) arr[r.Idx] = Math.Round(v, 2);
        }
        return new
        {
            range, step_minutes = step,
            start = TimeZoneInfo.ConvertTime(start, a.Zone).ToString("yyyy-MM-dd'T'HH:mm:ss"),
            start_utc = start, values,
        };
    }
}
