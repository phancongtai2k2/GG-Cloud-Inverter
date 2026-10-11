using System.Text.Json;

namespace GgPower.Domain;

/// <summary>
/// One <c>up/telemetry</c> message. A field the device could not read is absent from the payload and
/// stays null here: missing never means zero.
/// Sign conventions follow the app: <see cref="GridW"/> + import / − export, <see cref="BattW"/> + charging / − discharging
/// (the firmware reports battery flow with the opposite sign).
/// </summary>
public sealed record TelemetrySample(
    string Serial, DateTimeOffset Ts, int? InvStatus, double? InvTemp,
    double? Pv1V, double? Pv1A, double? Pv1W, double? Pv2V, double? Pv2A, double? Pv2W, double? PvW,
    double? GridV, double? GridA, double? GridHz, double? GridW,
    double? LoadV, double? LoadA, double? LoadHz, double? LoadW,
    double? BattW, double? Soc,
    double? YieldToday, double? YieldTotal, double? LoadToday, double? ExportToday, double? ImportToday,
    // What the inverter says about a battery being connected; null when the datalogger does not report it.
    bool? BatteryPresent = null,
    // Seconds this sample stands for when it was stored by the datalogger during an outage; null for live samples.
    int? SpanS = null);

/// <summary>One <c>up/battery</c> message. <see cref="CurrentA"/> + charging / − discharging.</summary>
public sealed record BatterySample(
    string Serial, DateTimeOffset Ts, string? BmsSerial, string? Model,
    double? Soc, double? Soh, double? Voltage, double? CurrentA, double? PowerW,
    double? TempCells, double? TempBms, int? Cycles, double? CapRemainingAh, double? CapNominalAh, double? EstMinutes,
    bool? Present = null, string? BmsComm = null, int? BatteryType = null);

public sealed record DeviceStatus(
    string Serial, bool Online, DateTimeOffset Ts, string? Reason, string? Model, string? InverterFw,
    string? LoggerFw, string? InverterComm, string? Ip, int? Rssi, string? LoggerSn = null, string? ConnType = null);

public sealed record AlarmEvent(
    string Serial, string AlarmId, DateTimeOffset Ts, bool Triggered, string Code, string Severity,
    string Title, string Description, JsonElement? Snapshot);

public sealed record CommandAck(string CmdId, string Serial, string Action, bool Success, int Code, string Message);

public static class Payloads
{
    /// <summary>Device timestamps outside this window (clock not set yet, replayed message) are replaced by the receive time.</summary>
    public static DateTimeOffset SaneTs(long? unixSeconds, DateTimeOffset receivedAt)
    {
        if (unixSeconds is not { } s) return receivedAt;
        if (s < receivedAt.AddDays(-1).ToUnixTimeSeconds() || s > receivedAt.AddMinutes(5).ToUnixTimeSeconds()) return receivedAt;
        return DateTimeOffset.FromUnixTimeSeconds(s);
    }

    /// <summary>How far back a stored sample may lie and still be accepted.</summary>
    public static readonly TimeSpan BackfillWindow = TimeSpan.FromDays(14);

    /// <summary>Time of a stored sample. Unlike a live one it is never replaced by the receive time: unknown means unusable.</summary>
    public static DateTimeOffset? BackfillTs(long? unixSeconds, DateTimeOffset receivedAt)
    {
        if (unixSeconds is not { } s) return null;
        if (s < (receivedAt - BackfillWindow).ToUnixTimeSeconds() || s > receivedAt.AddMinutes(5).ToUnixTimeSeconds()) return null;
        return DateTimeOffset.FromUnixTimeSeconds(s);
    }

    public static bool? Bool(JsonElement j, params string[] path)
    {
        foreach (var p in path)
        {
            if (j.ValueKind != JsonValueKind.Object || !j.TryGetProperty(p, out j)) return null;
        }
        return j.ValueKind switch { JsonValueKind.True => true, JsonValueKind.False => false, _ => null };
    }

    /// <param name="backfillSpanS">Set for a sample from <c>up/backfill</c>: the interval it was stored at.</param>
    public static TelemetrySample? Telemetry(JsonElement j, string topicSerial, DateTimeOffset receivedAt, int? backfillSpanS = null)
    {
        if (j.ValueKind != JsonValueKind.Object) return null;
        DateTimeOffset ts;
        if (backfillSpanS is null) ts = SaneTs(Long(j, "ts"), receivedAt);
        else if (BackfillTs(Long(j, "ts"), receivedAt) is { } stored) ts = stored;
        else return null;
        var batt = Num(j, "battery", "p_flow");
        return new TelemetrySample(
            topicSerial, ts, Int(j, "inv_status"), Num(j, "inv_temp"),
            Num(j, "pv", "v_pv1"), Num(j, "pv", "i_pv1"), Num(j, "pv", "p_pv1"),
            Num(j, "pv", "v_pv2"), Num(j, "pv", "i_pv2"), Num(j, "pv", "p_pv2"), Num(j, "pv", "p_total"),
            Num(j, "grid", "v_ac"), Num(j, "grid", "i_ac"), Num(j, "grid", "freq"), Num(j, "grid", "p_active"),
            Num(j, "load", "v_out"), Num(j, "load", "i_out"), Num(j, "load", "freq"), Num(j, "load", "p_active"),
            batt is { } b ? -b : null, Num(j, "battery", "soc"),
            Num(j, "energy", "yield_today_kwh"), Num(j, "energy", "yield_total_kwh"), Num(j, "energy", "load_today_kwh"),
            Num(j, "energy", "grid_export_today_kwh"), Num(j, "energy", "grid_import_today_kwh"),
            Bool(j, "battery", "present"), backfillSpanS);
    }

    public static BatterySample? Battery(JsonElement j, string topicSerial, DateTimeOffset receivedAt, bool backfill = false)
    {
        if (j.ValueKind != JsonValueKind.Object) return null;
        DateTimeOffset ts;
        if (!backfill) ts = SaneTs(Long(j, "ts"), receivedAt);
        else if (BackfillTs(Long(j, "ts"), receivedAt) is { } stored) ts = stored;
        else return null;
        var current = Num(j, "current");
        var comm = Str(j, "bms_comm");
        return new BatterySample(
            topicSerial, ts, Str(j, "bms_sn"), Str(j, "model"),
            Num(j, "soc"), Num(j, "soh"), Num(j, "voltage"), current is { } c ? -c : null, Num(j, "power_w"),
            Num(j, "temp_cells_avg"), Num(j, "temp_bms"), Int(j, "cycle_count"),
            Num(j, "capacity_ah_remaining"), Num(j, "capacity_ah_nominal"), Num(j, "est_time_remaining_min"),
            Bool(j, "present"), comm is "ok" or "fault" or "disabled" or "unknown" ? comm : null, Int(j, "battery_type"));
    }

    public static DeviceStatus? Status(JsonElement j, string topicSerial, DateTimeOffset receivedAt)
    {
        if (j.ValueKind != JsonValueKind.Object) return null;
        var state = Str(j, "state");
        if (state is not ("online" or "offline")) return null;
        return new DeviceStatus(topicSerial, state == "online", receivedAt, Str(j, "reason"), Str(j, "model"), Str(j, "fw_ver"),
            Str(j, "logger_fw"), Str(j, "inverter_comm"), Str(j, "ip_addr"), Int(j, "signal_rssi"),
            // Not sent by current firmware (requested 11/10/2026): the datalogger's own serial and its link type.
            Str(j, "logger_sn"), Str(j, "conn_type") is "wifi" or "ethernet" or "4g" ? Str(j, "conn_type") : null);
    }

    public static AlarmEvent? Alarm(JsonElement j, string topicSerial, DateTimeOffset receivedAt)
    {
        if (j.ValueKind != JsonValueKind.Object) return null;
        var id = Str(j, "alarm_id");
        var state = Str(j, "event_state");
        if (string.IsNullOrEmpty(id) || state is not ("triggered" or "cleared")) return null;
        var severity = Str(j, "severity") switch { "critical" => "error", "warning" => "warning", _ => "info" };
        JsonElement? snap = j.TryGetProperty("data_snapshot", out var s) && s.ValueKind == JsonValueKind.Object ? s.Clone() : null;
        return new AlarmEvent(topicSerial, Cap(id, 64)!, SaneTs(Long(j, "ts"), receivedAt), state == "triggered",
            Cap(Str(j, "error_code") ?? "UNKNOWN", 32)!, severity, Cap(Str(j, "title") ?? "", 200)!, Cap(Str(j, "description") ?? "", 1000)!, snap);
    }

    public static CommandAck? Ack(JsonElement j, string topicSerial)
    {
        if (j.ValueKind != JsonValueKind.Object) return null;
        var id = Str(j, "cmd_id");
        if (string.IsNullOrEmpty(id)) return null;
        var success = j.TryGetProperty("success", out var s) && s.ValueKind == JsonValueKind.True;
        return new CommandAck(id, topicSerial, Str(j, "action") ?? "", success, Int(j, "code") ?? 0, Cap(Str(j, "message") ?? "", 500)!);
    }

    public static double? Num(JsonElement j, params string[] path)
    {
        foreach (var p in path)
        {
            if (j.ValueKind != JsonValueKind.Object || !j.TryGetProperty(p, out j)) return null;
        }
        return j.ValueKind == JsonValueKind.Number && j.TryGetDouble(out var d) && double.IsFinite(d) ? d : null;
    }

    public static int? Int(JsonElement j, params string[] path) => Num(j, path) is { } d && Math.Abs(d) < int.MaxValue ? (int)Math.Round(d) : null;

    public static long? Long(JsonElement j, string name) =>
        j.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out var l) ? l : null;

    public static string? Str(JsonElement j, string name) =>
        j.ValueKind == JsonValueKind.Object && j.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    private static string? Cap(string? s, int n) => s is null ? null : s.Length <= n ? s : s[..n];
}

/// <summary>Parses <c>solar/{tenant}/inverter/{sn}/up/{kind...}</c>.</summary>
public readonly record struct UpTopic(string Tenant, string Serial, string Kind)
{
    public static UpTopic? Parse(string topic)
    {
        var p = topic.Split('/');
        if (p.Length < 6 || p[0] != "solar" || p[2] != "inverter" || p[4] != "up") return null;
        if (p[1].Length is 0 or > 32 || !IsSerial(p[3])) return null;
        return new UpTopic(p[1], p[3], string.Join('/', p[5..]));
    }

    public static bool IsSerial(string s) => s.Length is > 0 and <= 64 && s.All(ch => char.IsAsciiLetterOrDigit(ch) || ch is '-' or '_');

    public static string Down(string tenant, string serial) => $"solar/{tenant}/inverter/{serial}/down/command";
}

/// <summary>
/// Estimate of where the energy currently stored in the battery came from (PV or grid), kept
/// proportional on discharge. The rule (proportional vs FIFO) is still an open product question.
/// </summary>
public static class BatteryOrigin
{
    public static (double FromPv, double FromGrid) Step(double fromPv, double fromGrid, double? pvW, double? battW, double? soc, double? capacityKwh, double dtSeconds)
    {
        if (battW is { } b && dtSeconds > 0)
        {
            var kwh = Math.Abs(b) * dtSeconds / 3.6e6;
            if (b > 0)
            {
                var pvShare = b <= 0 ? 0 : Math.Clamp(Math.Max(pvW ?? 0, 0) / b, 0, 1);
                fromPv += kwh * pvShare;
                fromGrid += kwh * (1 - pvShare);
            }
            else if (fromPv + fromGrid > 0)
            {
                var keep = Math.Max(0, 1 - kwh / (fromPv + fromGrid));
                fromPv *= keep;
                fromGrid *= keep;
            }
        }
        // Keep the estimate consistent with what the BMS reports as stored.
        if (capacityKwh is > 0 && soc is { } s)
        {
            var stored = capacityKwh.Value * Math.Clamp(s, 0, 100) / 100;
            var total = fromPv + fromGrid;
            if (total <= 0) { fromPv = stored; fromGrid = 0; }   // unknown history: assume PV until data says otherwise
            else { fromPv *= stored / total; fromGrid *= stored / total; }
        }
        return (fromPv, fromGrid);
    }
}
