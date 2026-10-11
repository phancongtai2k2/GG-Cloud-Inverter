using System.Collections.Concurrent;
using System.Text.Json;
using System.Threading.Channels;
using GgPower.Domain;

namespace GgPower.Infrastructure;

/// <summary>Latest known state of one device, kept in memory so reads never touch the database.</summary>
public sealed class DeviceLive
{
    public required string Serial { get; init; }
    public string Tenant { get; set; } = "";
    public bool Online { get; set; }
    public DateTimeOffset LastSeen { get; set; }
    public string? Model { get; set; }
    public string? InverterFw { get; set; }
    public string? LoggerFw { get; set; }
    public string? InverterComm { get; set; }
    /// <summary>Datalogger link as last reported in <c>up/status</c>: IP, Wi-Fi RSSI (dBm), own serial and link type when sent.</summary>
    public string? LoggerIp { get; set; }
    public int? LoggerRssi { get; set; }
    public string? LoggerSn { get; set; }
    public string? LoggerConn { get; set; }
    public TelemetrySample? Telemetry { get; set; }
    public BatterySample? Battery { get; set; }
    public JsonElement? ConfigState { get; set; }
    public DateTimeOffset? ConfigStateAt { get; set; }
    public JsonElement? Firmware { get; set; }
    public double? BatteryCapacityKwh { get; set; }
    public double? RatedPowerKw { get; set; }
    public double BattFromPvKwh { get; set; }
    public double BattFromGridKwh { get; set; }
    /// <summary>Energy charged into the battery since local midnight, refreshed by the roll-up job; null until it first ran.</summary>
    public ChargedToday? ChargedToday { get; set; }
    public int OpenErrors { get; set; }
    public int OpenWarnings { get; set; }
    public int Unresolved { get; set; }
    /// <summary>Faults the inverter reports as present right now (open alerts raised by the device), oldest first.</summary>
    public ActiveError[] ActiveErrors { get; set; } = [];
    public bool Dirty { get; set; }
    public readonly object Gate = new();

    /// <summary>A device is treated as offline when nothing arrived for this long, even without a last-will message.</summary>
    public static readonly TimeSpan StaleAfter = TimeSpan.FromSeconds(90);

    public bool IsReachable(DateTimeOffset now) => Online && now - LastSeen <= StaleAfter;

    /// <summary>
    /// Whether a battery is connected, exactly as the inverter last reported it (battery message first,
    /// then telemetry). Null when the datalogger does not report it: the backend never guesses.
    /// </summary>
    public bool? BatteryPresent => Battery?.Present ?? Telemetry?.BatteryPresent;

    /// <summary>
    /// The inverter has no working link to the battery's BMS (no battery, or the link reported as fault / disabled), so the
    /// backup SOC cannot be applied. "unknown" or not reported does not block.
    /// </summary>
    public bool BmsDisconnected => BatteryPresent == false || Battery?.BmsComm is "fault" or "disabled";

    /// <summary>online | warning | error | offline (the app's ProjectStatus).</summary>
    public string StatusName(DateTimeOffset now) =>
        !IsReachable(now) ? "offline"
        : OpenErrors > 0 ? "error"
        : OpenWarnings > 0 || InverterComm is not (null or "ok") ? "warning"
        : "online";
}

public sealed record ActiveError(string Code, string Title);

/// <summary>kWh charged into the battery today, by source (PV charge and the rest, which came from the grid).</summary>
public sealed record ChargedToday(double FromPvKwh, double FromGridKwh);

public sealed class LiveStateCache
{
    private readonly ConcurrentDictionary<string, DeviceLive> _devices = new(StringComparer.OrdinalIgnoreCase);

    public DeviceLive? Get(string serial) => _devices.TryGetValue(serial, out var d) ? d : null;

    public DeviceLive GetOrAdd(string serial, string tenant, out bool created)
    {
        var isNew = false;
        var d = _devices.GetOrAdd(serial, s => { isNew = true; return new DeviceLive { Serial = s, Tenant = tenant }; });
        created = isNew;
        return d;
    }

    public void Load(DeviceLive d) => _devices[d.Serial] = d;

    public IEnumerable<DeviceLive> All => _devices.Values;
}

/// <summary>
/// Fan-out of device updates to WebSocket clients. Each client has a small bounded queue; when a
/// client is slow, its oldest update is dropped (only the newest state matters).
/// </summary>
public sealed class RealtimeHub
{
    public sealed class Subscription : IDisposable
    {
        private readonly RealtimeHub _hub;
        internal readonly HashSet<string> Serials = new(StringComparer.OrdinalIgnoreCase);
        internal readonly Channel<byte[]> Queue = Channel.CreateBounded<byte[]>(
            new BoundedChannelOptions(32) { FullMode = BoundedChannelFullMode.DropOldest, SingleReader = true });

        internal Subscription(RealtimeHub hub) => _hub = hub;
        public ChannelReader<byte[]> Reader => Queue.Reader;
        public void Push(byte[] message) => Queue.Writer.TryWrite(message);
        public void Dispose() => _hub.Remove(this);
    }

    private readonly ConcurrentDictionary<string, ImmutableSubs> _bySerial = new(StringComparer.OrdinalIgnoreCase);
    private sealed record ImmutableSubs(Subscription[] Items);
    private int _connections;

    public int Connections => _connections;

    public Subscription Connect()
    {
        Interlocked.Increment(ref _connections);
        return new Subscription(this);
    }

    public void SetSerials(Subscription s, IEnumerable<string> serials)
    {
        lock (s.Serials)
        {
            foreach (var old in s.Serials) Detach(old, s);
            s.Serials.Clear();
            foreach (var serial in serials)
            {
                if (!s.Serials.Add(serial)) continue;
                _bySerial.AddOrUpdate(serial, _ => new ImmutableSubs([s]), (_, cur) => new ImmutableSubs([.. cur.Items, s]));
            }
        }
    }

    private void Remove(Subscription s)
    {
        lock (s.Serials)
        {
            foreach (var serial in s.Serials) Detach(serial, s);
            s.Serials.Clear();
        }
        s.Queue.Writer.TryComplete();
        Interlocked.Decrement(ref _connections);
    }

    private void Detach(string serial, Subscription s)
    {
        while (_bySerial.TryGetValue(serial, out var cur))
        {
            var next = cur.Items.Where(x => !ReferenceEquals(x, s)).ToArray();
            if (next.Length == 0 ? _bySerial.TryRemove(new KeyValuePair<string, ImmutableSubs>(serial, cur))
                                 : _bySerial.TryUpdate(serial, new ImmutableSubs(next), cur)) return;
        }
    }

    public bool HasListeners(string serial) => _bySerial.ContainsKey(serial);

    public void Publish(string serial, byte[] message)
    {
        if (!_bySerial.TryGetValue(serial, out var subs)) return;
        foreach (var s in subs.Items) s.Push(message);
    }
}

/// <summary>The JSON pushed to the app for one device (also returned by REST for the first paint).</summary>
public static class LiveJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.Never,
    };

    /// <summary>
    /// Grid emission factor for "CO₂ avoided" (kg per kWh of PV yield). Set once at startup from <c>Stats:Co2KgPerKwh</c>
    /// so the realtime frame and the REST statistics agree.
    /// </summary>
    public static double Co2KgPerKwh { get; set; } = 0.6766;

    public static double? Co2Kg(double? yieldKwh) => yieldKwh is { } y ? Math.Round(y * Co2KgPerKwh, 1) : null;

    public static object? Charged(ChargedToday? c) => c is null ? null : new
    {
        from_pv_kwh = Math.Round(c.FromPvKwh, 2), from_grid_kwh = Math.Round(c.FromGridKwh, 2),
    };

    /// <summary>
    /// Datalogger card: link as last reported. <c>serial</c> / <c>connection</c> come from <c>logger_sn</c> / <c>conn_type</c>
    /// once the firmware sends them; until then serial is null and the link is the Wi-Fi the product ships with.
    /// </summary>
    public static object Datalogger(DeviceLive d, bool reachable) => new
    {
        serial = d.LoggerSn, online = reachable, connection = d.LoggerConn ?? "wifi",
        rssi_dbm = d.LoggerRssi, ip = d.LoggerIp, firmware = d.LoggerFw,
    };

    public static object Snapshot(DeviceLive d, DateTimeOffset now)
    {
        lock (d.Gate)
        {
            var t = d.Telemetry;
            var b = d.Battery;
            var reachable = d.IsReachable(now);
            var present = d.BatteryPresent;
            // The inverter reports "no battery": its battery readings describe the charger output, not a battery.
            var none = present == false;
            return new
            {
                type = "device",
                serial = d.Serial,
                status = d.StatusName(now),
                updated_at = d.LastSeen,
                inverter_comm = d.InverterComm,
                model = d.Model,
                inverter_fw = d.InverterFw,
                logger_fw = d.LoggerFw,
                unresolved_alerts = d.Unresolved,
                // Known even while the device is unreachable: it is the last state the inverter reported.
                active_errors = d.ActiveErrors.Select(e => new { code = e.Code, title = e.Title }).ToArray(),
                // true / false as reported by the inverter, null when it is not reported.
                battery_present = present,
                bms_comm = b?.BmsComm,
                // Live values are only meaningful while the device is reachable.
                telemetry = t is null || !reachable ? null : new
                {
                    ts = t.Ts, inv_status = t.InvStatus, inv_temp_c = t.InvTemp,
                    pv1_v = t.Pv1V, pv1_a = t.Pv1A, pv1_w = t.Pv1W, pv2_v = t.Pv2V, pv2_a = t.Pv2A, pv2_w = t.Pv2W, pv_w = t.PvW,
                    grid_v = t.GridV, grid_a = t.GridA, grid_hz = t.GridHz, grid_w = t.GridW,
                    load_v = t.LoadV, load_a = t.LoadA, load_hz = t.LoadHz, load_w = t.LoadW,
                    battery_w = none ? null : t.BattW, soc_percent = none ? null : t.Soc,
                    yield_today_kwh = t.YieldToday, yield_total_kwh = t.YieldTotal, load_today_kwh = t.LoadToday,
                    export_today_kwh = t.ExportToday, import_today_kwh = t.ImportToday,
                },
                battery = b is null || !reachable || none ? null : new
                {
                    ts = b.Ts, present = b.Present, bms_comm = b.BmsComm, battery_type = b.BatteryType, bms_serial = b.BmsSerial, model = b.Model, soc_percent = b.Soc, soh_percent = b.Soh,
                    voltage_v = b.Voltage, current_a = b.CurrentA, power_w = b.PowerW,
                    temp_cells_c = b.TempCells, temp_bms_c = b.TempBms, cycles = b.Cycles,
                    capacity_remaining_ah = b.CapRemainingAh, capacity_nominal_ah = b.CapNominalAh, est_minutes = b.EstMinutes,
                    // Limit lines of the battery screen: charging stops at charge_stop_soc (when the firmware reports it),
                    // discharging stops at the backup SOC.
                    charge_limit_soc_percent = d.ConfigState is { } cs1 ? Payloads.Int(cs1, "charge_stop_soc") : null,
                    discharge_limit_soc_percent = ConfigMapping.BackupSoc(d.ConfigState),
                },
                datalogger = Datalogger(d, reachable),
                battery_content = d.BatteryCapacityKwh is > 0 && !none ? new
                {
                    capacity_kwh = d.BatteryCapacityKwh, from_pv_kwh = Math.Round(d.BattFromPvKwh, 3), from_grid_kwh = Math.Round(d.BattFromGridKwh, 3),
                } : null,
                // Home screen chip: today's yield × emission factor (null while the yield is not known).
                co2_today_kg = t is null || !reachable ? null : Co2Kg(t.YieldToday),
                // Home screen battery card: kWh charged since local midnight (from the roll-up, ~30 s behind).
                battery_charged_today = none ? null : Charged(d.ChargedToday),
            };
        }
    }

    public static byte[] Bytes(object o) => JsonSerializer.SerializeToUtf8Bytes(o, Options);
}
