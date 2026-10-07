using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace GgPower.Domain;

public sealed record TimeOfDay(int Hour, int Minute)
{
    // Derived values: not part of the stored / transmitted JSON.
    [System.Text.Json.Serialization.JsonIgnore] public int Minutes => Hour * 60 + Minute;
    [System.Text.Json.Serialization.JsonIgnore] public string Hhmm => $"{Hour:D2}:{Minute:D2}";
    [System.Text.Json.Serialization.JsonIgnore] public bool Valid => Hour is >= 0 and <= 23 && Minute is >= 0 and <= 59;
}

public sealed record TouSlot(TimeOfDay Start, TimeOfDay End, int StopSocPercent, int MaxPowerW);

public sealed record TouStrategy(string Id, string Name, List<string> Weekdays, List<TouSlot> ChargeSlots, List<TouSlot> DischargeSlots);

public sealed record FieldError(string Field, string Code);

public static class OperatingModes
{
    public const string SelfConsumption = "self_consumption";
    public const string BatteryPriority = "battery_priority";
    public const string MaxGridExport = "max_grid_export";
    public const string Tou = "tou";
    public const string Unknown = "unknown";

    public static readonly string[] Writable = [SelfConsumption, BatteryPriority, MaxGridExport, Tou];

    /// <summary>Modes a role may select. Exporting to the grid is not offered to home owners.</summary>
    public static string[] WritableFor(string role) =>
        role == Roles.Owner ? [SelfConsumption, BatteryPriority, Tou] : Writable;
}

public static class WriteGroups
{
    public const string OperatingMode = "operating_mode";
    public const string BackupSoc = "backup_soc";
    public const string TouSchedule = "tou_schedule";

    /// <summary>
    /// Groups every datalogger firmware can execute. The backup SOC is added per device, when its
    /// firmware reports that it can write it (<see cref="ConfigMapping.SupportsBackupSoc"/>).
    /// </summary>
    public static readonly string[] SupportedByDevice = [OperatingMode, TouSchedule];
}

public static class TouRules
{
    public static readonly string[] Days = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"];
    public const int MaxStrategies = 4, MaxSlots = 3, MaxPowerW = 12000, NameMax = 40;

    public static List<FieldError> Validate(IReadOnlyList<TouStrategy> strategies)
    {
        var errors = new List<FieldError>();
        if (strategies.Count > MaxStrategies) errors.Add(new("strategies", "TOO_MANY_STRATEGIES"));
        var usedDays = new HashSet<string>();
        var ids = new HashSet<string>();
        for (var i = 0; i < strategies.Count; i++)
        {
            var s = strategies[i];
            var at = $"strategies[{i}]";
            if (!Guid.TryParse(s.Id, out _) || !ids.Add(s.Id)) errors.Add(new($"{at}.id", "ID_INVALID"));
            if (string.IsNullOrWhiteSpace(s.Name) || s.Name.Length > NameMax) errors.Add(new($"{at}.name", "NAME_INVALID"));
            if (s.Weekdays.Count == 0) errors.Add(new($"{at}.weekdays", "WEEKDAYS_EMPTY"));
            foreach (var d in s.Weekdays)
            {
                if (!Days.Contains(d)) errors.Add(new($"{at}.weekdays", "WEEKDAY_INVALID"));
                else if (!usedDays.Add(d)) errors.Add(new($"{at}.weekdays", "WEEKDAY_DUPLICATE"));
            }
            ValidateSlots(s.ChargeSlots, $"{at}.charge_slots", errors);
            ValidateSlots(s.DischargeSlots, $"{at}.discharge_slots", errors);
            // A charge slot and a discharge slot may not overlap either.
            var all = s.ChargeSlots.Select((x, k) => (x, $"{at}.charge_slots[{k}]"))
                .Concat(s.DischargeSlots.Select((x, k) => (x, $"{at}.discharge_slots[{k}]"))).ToList();
            for (var a = 0; a < all.Count; a++)
                for (var b = a + 1; b < all.Count; b++)
                    if (Overlap(all[a].x, all[b].x) && !errors.Any(e => e.Field == all[b].Item2 && e.Code == "SLOT_OVERLAP"))
                        errors.Add(new(all[b].Item2, "SLOT_OVERLAP"));
        }
        return errors;
    }

    private static void ValidateSlots(List<TouSlot> slots, string at, List<FieldError> errors)
    {
        if (slots.Count > MaxSlots) errors.Add(new(at, "TOO_MANY_SLOTS"));
        for (var i = 0; i < slots.Count; i++)
        {
            var s = slots[i];
            if (!s.Start.Valid || !s.End.Valid) errors.Add(new($"{at}[{i}]", "TIME_INVALID"));
            else if (s.Start.Minutes == s.End.Minutes) errors.Add(new($"{at}[{i}]", "SLOT_EMPTY"));
            if (s.StopSocPercent is < 0 or > 100) errors.Add(new($"{at}[{i}].stop_soc_percent", "VALUE_OUT_OF_RANGE"));
            if (s.MaxPowerW is < 0 or > MaxPowerW) errors.Add(new($"{at}[{i}].max_power_w", "VALUE_OUT_OF_RANGE"));
        }
    }

    /// <summary>Slots may wrap past midnight; touching edges do not overlap.</summary>
    public static bool Overlap(TouSlot a, TouSlot b)
    {
        foreach (var (s1, e1) in Segments(a))
            foreach (var (s2, e2) in Segments(b))
                if (s1 < e2 && s2 < e1) return true;
        return false;
    }

    private static IEnumerable<(int, int)> Segments(TouSlot s)
    {
        var (a, b) = (s.Start.Minutes, s.End.Minutes);
        if (a < b) yield return (a, b);
        else if (a > b) { yield return (a, 1440); yield return (0, b); }
    }

    public static TouStrategy? ForDay(IEnumerable<TouStrategy> strategies, DayOfWeek day)
    {
        var key = Days[((int)day + 6) % 7];
        return strategies.FirstOrDefault(s => s.Weekdays.Contains(key));
    }
}

/// <summary>One command for the datalogger (see doc/MQTT_API.md, section 4).</summary>
public sealed record DeviceCommand(string Action, JsonObject Params);

/// <summary>What the inverter must report in <c>up/config/state</c> once a plan has been applied.</summary>
public sealed record ExpectedState(
    string? PvPriority, string? GridMode, bool? ScheduleEnabled, List<TouSlot>? ChargeSlots,
    bool? DischargeEnabled = null, List<TouSlot>? DischargeSlots = null,
    // Days of the week the inverter itself runs its timed periods on; null when not written.
    List<string>? ScheduleDays = null,
    int? BackupSoc = null);

public sealed record Change(string Parameter, object? Old, object? New)
{
    public object? Readback { get; set; }
    public bool? ReadbackMatched { get; set; }
}

/// <summary>Translation between the app's configuration model and the datalogger's settings.</summary>
public static class ConfigMapping
{
    private static string? S(JsonElement? state, string name) => state is { } s ? Payloads.Str(s, name) : null;

    private static bool Enabled(JsonElement? state, string schedule) =>
        state is { ValueKind: JsonValueKind.Object } s && s.TryGetProperty(schedule, out var cs) && cs.ValueKind == JsonValueKind.Object &&
        cs.TryGetProperty("enabled", out var en) && en.ValueKind == JsonValueKind.True;

    /// <summary>Timed charging is switched on.</summary>
    public static bool ScheduleEnabled(JsonElement? state) => Enabled(state, "charge_schedule");

    /// <summary>Timed discharging is switched on.</summary>
    public static bool DischargeEnabled(JsonElement? state) => Enabled(state, "discharge_schedule");

    /// <summary>
    /// The datalogger reports <c>discharge_schedule</c> only when its firmware (1.0.17+) and the inverter
    /// support timed discharge; that is also when it accepts SET_DISCHARGE_SCHEDULE.
    /// </summary>
    /// <summary>
    /// Days of the week on which the inverter runs its timed periods ("mon".."sun"), as it reports them;
    /// null when the datalogger does not report them (firmware before 1.0.24, or a model without the setting).
    /// </summary>
    public static List<string>? ScheduleDays(JsonElement? state)
    {
        if (state is not { ValueKind: JsonValueKind.Object } s || !s.TryGetProperty("schedule_days", out var days) || days.ValueKind != JsonValueKind.Array) return null;
        return days.EnumerateArray().Where(d => d.ValueKind == JsonValueKind.String).Select(d => d.GetString()!).ToList();
    }

    private static bool SameDays(List<string>? a, List<string> b) => a is not null && a.Count == b.Count && !a.Except(b).Any();

    public static bool SupportsDischarge(JsonElement? state) =>
        state is { ValueKind: JsonValueKind.Object } s && s.TryGetProperty("discharge_schedule", out var ds) && ds.ValueKind == JsonValueKind.Object;

    public static int? BackupSoc(JsonElement? state) => state is { } s ? Payloads.Int(s, "battery_reserve_soc") : null;

    /// <summary>SOC at which the inverter uses the battery again after it reached the backup SOC.</summary>
    public static int? ResumeSoc(JsonElement? state) => state is { } s ? Payloads.Int(s, "battery_resume_soc") : null;

    public const int BackupSocMin = 5, BackupSocMax = 95;

    /// <summary>True when the datalogger can write the backup SOC (firmware 1.0.26 and later).</summary>
    public static bool SupportsBackupSoc(JsonElement? state) => state is { } s && Payloads.Bool(s, "battery_reserve_writable") == true;

    /// <summary>Groups this device can execute.</summary>
    public static string[] WritableGroups(JsonElement? state) =>
        SupportsBackupSoc(state) ? [WriteGroups.OperatingMode, WriteGroups.BackupSoc, WriteGroups.TouSchedule] : WriteGroups.SupportedByDevice;

    public static (List<DeviceCommand> Commands, ExpectedState Expected) PlanBackupSoc(int percent) =>
        ([new DeviceCommand("SET_BATTERY_RESERVE", new JsonObject { ["reserve_soc"] = percent })],
            new ExpectedState(null, null, null, null, BackupSoc: percent));

    /// <summary>
    /// The operating mode the inverter is in. <paramref name="touActive"/> is the backend's record that
    /// the user chose TOU: on a day without a strategy the inverter runs like self-consumption.
    /// </summary>
    public static string ReadMode(JsonElement? state, bool touActive)
    {
        var pv = S(state, "pv_priority");
        var grid = S(state, "grid_mode");
        return pv switch
        {
            "charging" => OperatingModes.BatteryPriority,
            "grid" when grid == "on_grid" => OperatingModes.MaxGridExport,
            "load" when grid is "limit_ups_load" or "limit_home_load" =>
                touActive || ScheduleEnabled(state) || DischargeEnabled(state) ? OperatingModes.Tou : OperatingModes.SelfConsumption,
            _ => OperatingModes.Unknown,
        };
    }

    private static List<TouSlot> ReadSlots(JsonElement? state, string schedule, string powerKey, int defaultSoc)
    {
        var list = new List<TouSlot>();
        if (state is not { ValueKind: JsonValueKind.Object } s || !s.TryGetProperty(schedule, out var cs) || cs.ValueKind != JsonValueKind.Object ||
            !cs.TryGetProperty("periods", out var periods) || periods.ValueKind != JsonValueKind.Array) return list;
        foreach (var p in periods.EnumerateArray())
        {
            var start = ParseTime(Payloads.Str(p, "start_time"));
            var end = ParseTime(Payloads.Str(p, "end_time"));
            if (start is null || end is null || start.Minutes == end.Minutes) continue;   // 00:00–00:00 = unused
            list.Add(new TouSlot(start, end, Payloads.Int(p, "stop_soc") ?? defaultSoc, Payloads.Int(p, powerKey) ?? 0));
        }
        return list;
    }

    public static List<TouSlot> ReadChargeSlots(JsonElement? state) => ReadSlots(state, "charge_schedule", "charge_power_w", 100);
    public static List<TouSlot> ReadDischargeSlots(JsonElement? state) => ReadSlots(state, "discharge_schedule", "discharge_power_w", 0);

    private static TimeOfDay? ParseTime(string? hhmm)
    {
        if (hhmm is null) return null;
        var p = hhmm.Split(':');
        return p.Length == 2 && int.TryParse(p[0], out var h) && int.TryParse(p[1], out var m) ? new TimeOfDay(h, m) : null;
    }

    /// <summary>
    /// Commands that take the inverter from <paramref name="state"/> to <paramref name="mode"/>.
    /// The grid mode is only touched when entering or leaving grid export, and is never set to off-grid.
    /// Timed charging and discharging run in TOU only: other modes switch them off.
    /// </summary>
    public static (List<DeviceCommand> Commands, ExpectedState Expected) PlanMode(string mode, JsonElement? state, TouStrategy? today, string? savedGridMode)
    {
        var commands = new List<DeviceCommand>();
        var currentGrid = S(state, "grid_mode");
        var work = new JsonObject();
        string pv;
        string? grid = null;
        switch (mode)
        {
            case OperatingModes.BatteryPriority: pv = "charging"; break;
            case OperatingModes.MaxGridExport: pv = "grid"; grid = "on_grid"; break;
            default: pv = "load"; break;
        }
        // AC coupling is an installation choice, not an operating mode: it is left alone.
        if (mode != OperatingModes.MaxGridExport && currentGrid == "on_grid")
            grid = savedGridMode is "limit_ups_load" or "limit_home_load" ? savedGridMode : "limit_home_load";
        work["pv_priority"] = pv;
        if (grid is not null) work["grid_mode"] = grid;
        commands.Add(new DeviceCommand("SET_WORK_MODE", work));

        var (schedule, expected) = PlanSchedule(state, mode == OperatingModes.Tou ? today : null);
        commands.AddRange(schedule);
        return (commands, expected with { PvPriority = pv, GridMode = grid });
    }

    /// <summary>
    /// Commands that load <paramref name="today"/>'s slots (null: no strategy applies, both schedules off).
    /// Each schedule is written only when it has slots, or needs switching off.
    /// </summary>
    public static (List<DeviceCommand> Commands, ExpectedState Expected) PlanSchedule(JsonElement? state, TouStrategy? today)
    {
        var commands = new List<DeviceCommand>();
        var charge = today is { ChargeSlots.Count: > 0 } ? today.ChargeSlots : null;
        if (charge is not null) commands.Add(new DeviceCommand("SET_CHARGE_SCHEDULE", ChargeParams(charge)));
        else if (ScheduleEnabled(state)) commands.Add(new DeviceCommand("SET_CHARGE_SCHEDULE", new JsonObject { ["enabled"] = false }));

        bool? dischargeEnabled = null;
        List<TouSlot>? discharge = null;
        if (SupportsDischarge(state))
        {
            discharge = today is { DischargeSlots.Count: > 0 } ? today.DischargeSlots : null;
            dischargeEnabled = discharge is not null;
            if (discharge is not null) commands.Add(new DeviceCommand("SET_DISCHARGE_SCHEDULE", DischargeParams(discharge)));
            else if (DischargeEnabled(state)) commands.Add(new DeviceCommand("SET_DISCHARGE_SCHEDULE", new JsonObject { ["enabled"] = false }));
        }
        // The inverter judges the days itself as well: it gets the days of the strategy whose slots it holds.
        // (On a day without a strategy both schedules are off, and the days are left as they are.)
        List<string>? days = null;
        if (today is not null && (charge is not null || discharge is not null) && ScheduleDays(state) is { } current)
        {
            days = TouRules.Days.Where(today.Weekdays.Contains).ToList();
            if (!SameDays(current, days))
                commands.Insert(0, new DeviceCommand("SET_SCHEDULE_DAYS", new JsonObject { ["days"] = new JsonArray(days.Select(d => (JsonNode)d).ToArray()) }));
        }
        return (commands, new ExpectedState(null, null, charge is not null, charge, dischargeEnabled, discharge, days));
    }

    // The inverter always holds three periods per schedule; unused ones are cleared with 00:00–00:00.
    private static JsonObject ChargeParams(List<TouSlot> slots)
    {
        var periods = new JsonArray();
        for (var i = 0; i < TouRules.MaxSlots; i++)
        {
            periods.Add(i < slots.Count
                ? new JsonObject
                {
                    ["start_time"] = slots[i].Start.Hhmm, ["end_time"] = slots[i].End.Hhmm,
                    ["charge_power_w"] = slots[i].MaxPowerW, ["stop_soc"] = slots[i].StopSocPercent, ["grid_charging"] = true,
                }
                : new JsonObject { ["start_time"] = "00:00", ["end_time"] = "00:00", ["grid_charging"] = false });
        }
        return new JsonObject { ["enabled"] = true, ["periods"] = periods };
    }

    private static JsonObject DischargeParams(List<TouSlot> slots)
    {
        var periods = new JsonArray();
        for (var i = 0; i < TouRules.MaxSlots; i++)
        {
            periods.Add(i < slots.Count
                ? new JsonObject
                {
                    ["start_time"] = slots[i].Start.Hhmm, ["end_time"] = slots[i].End.Hhmm,
                    ["discharge_power_w"] = slots[i].MaxPowerW, ["stop_soc"] = slots[i].StopSocPercent,
                }
                : new JsonObject { ["start_time"] = "00:00", ["end_time"] = "00:00" });
        }
        return new JsonObject { ["enabled"] = true, ["periods"] = periods };
    }

    /// <summary>
    /// Commands that write the settings in <paramref name="old"/> back exactly: every period with its own
    /// times, limits and grid-charging flag (also the unused ones), then the enable flags.
    /// </summary>
    public static (List<DeviceCommand> Commands, ExpectedState Expected) PlanRestore(JsonElement old)
    {
        var commands = new List<DeviceCommand>();
        var work = new JsonObject();
        if (Payloads.Str(old, "pv_priority") is { } pv) work["pv_priority"] = pv;
        if (Payloads.Str(old, "grid_mode") is { } gm) work["grid_mode"] = gm;
        if (work.Count > 0) commands.Add(new DeviceCommand("SET_WORK_MODE", work));
        if (ScheduleDays(old) is { } oldDays)
            commands.Add(new DeviceCommand("SET_SCHEDULE_DAYS", new JsonObject { ["days"] = new JsonArray(oldDays.Select(d => (JsonNode)d).ToArray()) }));

        commands.Add(new DeviceCommand("SET_CHARGE_SCHEDULE", RestoreParams(old, "charge_schedule", "charge_power_w", gridCharge: true)));
        var supportsDischarge = SupportsDischarge(old);
        if (supportsDischarge)
            commands.Add(new DeviceCommand("SET_DISCHARGE_SCHEDULE", RestoreParams(old, "discharge_schedule", "discharge_power_w", gridCharge: false)));

        var chargeOn = ScheduleEnabled(old);
        return (commands, new ExpectedState(
            Payloads.Str(old, "pv_priority"), Payloads.Str(old, "grid_mode"), chargeOn, chargeOn ? ReadChargeSlots(old) : null,
            supportsDischarge ? DischargeEnabled(old) : null, supportsDischarge && DischargeEnabled(old) ? ReadDischargeSlots(old) : null,
            ScheduleDays(old)));
    }

    private static JsonObject RestoreParams(JsonElement old, string schedule, string powerKey, bool gridCharge)
    {
        var result = new JsonObject { ["enabled"] = Enabled(old, schedule) };
        var list = new JsonArray();
        var periods = old.TryGetProperty(schedule, out var cs) && cs.ValueKind == JsonValueKind.Object &&
                      cs.TryGetProperty("periods", out var ps) && ps.ValueKind == JsonValueKind.Array ? ps.EnumerateArray().ToArray() : [];
        foreach (var p in periods)
        {
            if (Payloads.Int(p, "period") is not { } n || Payloads.Str(p, "start_time") is not { } start || Payloads.Str(p, "end_time") is not { } end) continue;
            var o = new JsonObject { ["period"] = n, ["start_time"] = start, ["end_time"] = end };
            if (Payloads.Int(p, powerKey) is { } w) o[powerKey] = w;
            if (Payloads.Int(p, "stop_soc") is { } soc) o["stop_soc"] = soc;
            if (gridCharge && p.TryGetProperty("grid_charge", out var gc) && gc.ValueKind is JsonValueKind.True or JsonValueKind.False)
                o["grid_charging"] = gc.GetBoolean();
            list.Add(o);
        }
        if (list.Count > 0) result["periods"] = list;
        // The datalogger refuses "enabled" without a period that carries times.
        else if ((bool)result["enabled"]!) result["enabled"] = false;
        return result;
    }

    private static bool SameSlots(List<TouSlot> have, List<TouSlot> want)
    {
        if (have.Count != want.Count) return false;
        for (var i = 0; i < want.Count; i++)
            if (have[i].Start != want[i].Start || have[i].End != want[i].End ||
                have[i].MaxPowerW != want[i].MaxPowerW || have[i].StopSocPercent != want[i].StopSocPercent) return false;
        return true;
    }

    public static bool Matches(ExpectedState e, JsonElement? state)
    {
        if (e.ScheduleDays is not null && !SameDays(ScheduleDays(state), e.ScheduleDays)) return false;
        if (e.BackupSoc is { } backup && BackupSoc(state) != backup) return false;
        if (e.PvPriority is not null && S(state, "pv_priority") != e.PvPriority) return false;
        if (e.GridMode is not null && S(state, "grid_mode") != e.GridMode) return false;
        if (e.ScheduleEnabled is { } en && ScheduleEnabled(state) != en) return false;
        if (e.ChargeSlots is { } charge && !SameSlots(ReadChargeSlots(state), charge)) return false;
        if (e.DischargeEnabled is { } den && DischargeEnabled(state) != den) return false;
        if (e.DischargeSlots is { } discharge && !SameSlots(ReadDischargeSlots(state), discharge)) return false;
        return true;
    }

    /// <summary>Opaque version of everything a user can change; any difference makes a pending edit stale.</summary>
    public static string Version(string mode, int? backupSoc, string strategiesJson, JsonElement? state)
    {
        static string Text(List<TouSlot> slots) => string.Join(";", slots.Select(s => $"{s.Start.Hhmm}-{s.End.Hhmm}/{s.MaxPowerW}/{s.StopSocPercent}"));
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(
            $"{mode}|{backupSoc}|{strategiesJson}|{Text(ReadChargeSlots(state))}|{Text(ReadDischargeSlots(state))}|{S(state, "grid_mode")}"));
        return "v-" + Convert.ToHexString(hash, 0, 4).ToLowerInvariant();
    }
}
