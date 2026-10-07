using System.Text.Json;
using GgPower.Domain;
using GgPower.Infrastructure;

namespace GgPower.Tests;

public class TouRulesTests
{
    private static TouSlot Slot(int h1, int h2, int soc = 90, int w = 3000) => new(new TimeOfDay(h1, 0), new TimeOfDay(h2, 0), soc, w);

    private static TouStrategy Strategy(string[] days, TouSlot[]? charge = null, TouSlot[]? discharge = null, string? id = null) =>
        new(id ?? Guid.NewGuid().ToString(), "S", days.ToList(), (charge ?? []).ToList(), (discharge ?? []).ToList());

    [Theory]
    [InlineData(8, 10, 9, 11, true)]     // plain overlap
    [InlineData(8, 10, 10, 12, false)]   // touching edges do not overlap
    [InlineData(22, 4, 3, 5, true)]      // slot wrapping past midnight
    [InlineData(22, 4, 4, 6, false)]     // wrap, touching at the end
    [InlineData(22, 4, 23, 1, true)]     // both wrap
    [InlineData(22, 2, 2, 22, false)]    // exact complement
    public void Overlap(int a1, int a2, int b1, int b2, bool expected)
    {
        Assert.Equal(expected, TouRules.Overlap(Slot(a1, a2), Slot(b1, b2)));
        Assert.Equal(expected, TouRules.Overlap(Slot(b1, b2), Slot(a1, a2)));
    }

    [Fact]
    public void ValidScheduleHasNoErrors() =>
        Assert.Empty(TouRules.Validate([
            Strategy(["mon", "tue"], [Slot(22, 4), Slot(12, 14)], [Slot(18, 21)]),
            Strategy(["sat", "sun"], [Slot(0, 6)]),
        ]));

    [Fact]
    public void ChargeAndDischargeSlotsMayNotOverlap()
    {
        var errors = TouRules.Validate([Strategy(["mon"], [Slot(10, 14)], [Slot(13, 15)])]);
        Assert.Contains(errors, e => e.Code == "SLOT_OVERLAP" && e.Field == "strategies[0].discharge_slots[0]");
    }

    [Fact]
    public void RejectsStructuralProblems()
    {
        Assert.Contains(TouRules.Validate([Strategy([])]), e => e.Code == "WEEKDAYS_EMPTY");
        Assert.Contains(TouRules.Validate([Strategy(["mon"]), Strategy(["mon"])]), e => e.Code == "WEEKDAY_DUPLICATE");
        Assert.Contains(TouRules.Validate([Strategy(["funday"])]), e => e.Code == "WEEKDAY_INVALID");
        Assert.Contains(TouRules.Validate([Strategy(["mon"], [Slot(1, 2), Slot(3, 4), Slot(5, 6), Slot(7, 8)])]), e => e.Code == "TOO_MANY_SLOTS");
        Assert.Contains(TouRules.Validate([Strategy(["mon"], [Slot(5, 5)])]), e => e.Code == "SLOT_EMPTY");
        Assert.Contains(TouRules.Validate([Strategy(["mon"], [Slot(1, 2, soc: 101)])]), e => e.Code == "VALUE_OUT_OF_RANGE");
        Assert.Contains(TouRules.Validate([Strategy(["mon"], [Slot(1, 2, w: 12001)])]), e => e.Code == "VALUE_OUT_OF_RANGE");
        Assert.Contains(TouRules.Validate([Strategy(["mon"], id: "not-a-uuid")]), e => e.Code == "ID_INVALID");
        var five = Enumerable.Range(0, 5).Select(i => Strategy([TouRules.Days[i]])).ToList();
        Assert.Contains(TouRules.Validate(five), e => e.Code == "TOO_MANY_STRATEGIES");
    }

    [Fact]
    public void StrategyForDay()
    {
        var week = Strategy(["mon", "tue", "wed", "thu", "fri"]);
        var sunday = Strategy(["sun"]);
        Assert.Same(week, TouRules.ForDay([week, sunday], DayOfWeek.Monday));
        Assert.Same(sunday, TouRules.ForDay([week, sunday], DayOfWeek.Sunday));
        Assert.Null(TouRules.ForDay([week, sunday], DayOfWeek.Saturday));
    }
}

public class ConfigMappingTests
{
    private static JsonElement State(string pv, string grid, bool schedule = false, params (string Start, string End, int W, int Soc)[] periods)
    {
        var list = Enumerable.Range(0, 3).Select(i => i < periods.Length
            ? new { start_time = periods[i].Start, end_time = periods[i].End, charge_power_w = periods[i].W, stop_soc = periods[i].Soc }
            : new { start_time = "00:00", end_time = "00:00", charge_power_w = 3300, stop_soc = 100 });
        var json = JsonSerializer.Serialize(new { pv_priority = pv, grid_mode = grid, charge_schedule = new { enabled = schedule, periods = list }, battery_reserve_soc = 10 });
        return JsonDocument.Parse(json).RootElement;
    }

    private static TouStrategy Today(params TouSlot[] charge) => new(Guid.NewGuid().ToString(), "T", TouRules.Days.ToList(), charge.ToList(), []);

    [Fact]
    public void ReadMode()
    {
        Assert.Equal(OperatingModes.BatteryPriority, ConfigMapping.ReadMode(State("charging", "limit_ups_load"), false));
        Assert.Equal(OperatingModes.SelfConsumption, ConfigMapping.ReadMode(State("load", "limit_home_load"), false));
        Assert.Equal(OperatingModes.Tou, ConfigMapping.ReadMode(State("load", "limit_home_load", schedule: true), false));
        // TOU chosen, but today has no strategy: the inverter runs like self-consumption and is still "tou".
        Assert.Equal(OperatingModes.Tou, ConfigMapping.ReadMode(State("load", "limit_home_load"), touActive: true));
        Assert.Equal(OperatingModes.MaxGridExport, ConfigMapping.ReadMode(State("grid", "on_grid"), false));
        Assert.Equal(OperatingModes.Unknown, ConfigMapping.ReadMode(State("grid", "limit_home_load"), false));
        Assert.Equal(OperatingModes.Unknown, ConfigMapping.ReadMode(State("load", "ac_coupling"), false));
        Assert.Equal(OperatingModes.Unknown, ConfigMapping.ReadMode(null, false));
        Assert.Equal(10, ConfigMapping.BackupSoc(State("load", "limit_home_load")));
    }

    [Fact]
    public void PlanSelfConsumptionOnlyTouchesPvPriority()
    {
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("charging", "limit_ups_load"), null, null);
        var cmd = Assert.Single(commands);
        Assert.Equal("SET_WORK_MODE", cmd.Action);
        Assert.Equal("load", (string?)cmd.Params["pv_priority"]);
        Assert.False(cmd.Params.ContainsKey("grid_mode"));
        Assert.True(ConfigMapping.Matches(expected, State("load", "limit_ups_load")));
        Assert.False(ConfigMapping.Matches(expected, State("charging", "limit_ups_load")));
    }

    [Fact]
    public void LeavingGridExportRestoresThePreviousGridModeAndNeverGoesOffGrid()
    {
        var (commands, _) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("grid", "on_grid"), null, savedGridMode: "limit_ups_load");
        Assert.Equal("limit_ups_load", (string?)commands[0].Params["grid_mode"]);
        (commands, _) = ConfigMapping.PlanMode(OperatingModes.BatteryPriority, State("grid", "on_grid"), null, savedGridMode: null);
        Assert.Equal("limit_home_load", (string?)commands[0].Params["grid_mode"]);
        (commands, _) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("grid", "on_grid"), null, savedGridMode: "off_grid");
        Assert.Equal("limit_home_load", (string?)commands[0].Params["grid_mode"]);
    }

    [Fact]
    public void AcCouplingInstallationIsLeftAlone()
    {
        var (commands, _) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("load", "ac_coupling"), null, null);
        Assert.False(commands[0].Params.ContainsKey("grid_mode"));
    }

    [Fact]
    public void GridExportIsTheOnlyModeThatEnablesExport()
    {
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.MaxGridExport, State("load", "limit_home_load"), null, null);
        Assert.Equal("on_grid", (string?)commands[0].Params["grid_mode"]);
        Assert.Equal("grid", (string?)commands[0].Params["pv_priority"]);
        Assert.Equal("on_grid", expected.GridMode);
        foreach (var mode in new[] { OperatingModes.SelfConsumption, OperatingModes.BatteryPriority, OperatingModes.Tou })
        {
            (commands, _) = ConfigMapping.PlanMode(mode, State("load", "limit_home_load"), Today(new TouSlot(new(1, 0), new(2, 0), 90, 1000)), null);
            Assert.DoesNotContain(commands, c => (string?)c.Params["grid_mode"] is "on_grid" or "ac_coupling");
            Assert.DoesNotContain(commands, c => (string?)c.Params["pv_priority"] == "grid");
        }
    }

    [Fact]
    public void TouLoadsTodaysChargeSlotsAndClearsUnusedPeriods()
    {
        var slot = new TouSlot(new(22, 0), new(4, 30), 90, 3000);
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.Tou, State("charging", "limit_home_load"), Today(slot), null);
        Assert.Equal(2, commands.Count);
        var schedule = commands[1];
        Assert.Equal("SET_CHARGE_SCHEDULE", schedule.Action);
        Assert.True((bool)schedule.Params["enabled"]!);
        var periods = schedule.Params["periods"]!.AsArray();
        Assert.Equal(3, periods.Count);
        Assert.Equal("22:00", (string?)periods[0]!["start_time"]);
        Assert.Equal("04:30", (string?)periods[0]!["end_time"]);
        Assert.Equal("00:00", (string?)periods[1]!["start_time"]);
        Assert.Equal("00:00", (string?)periods[2]!["end_time"]);

        Assert.True(ConfigMapping.Matches(expected, State("load", "limit_home_load", true, ("22:00", "04:30", 3000, 90))));
        Assert.False(ConfigMapping.Matches(expected, State("load", "limit_home_load", true, ("22:00", "04:30", 2500, 90))));
        Assert.False(ConfigMapping.Matches(expected, State("load", "limit_home_load", false, ("22:00", "04:30", 3000, 90))));
    }

    [Fact]
    public void LeavingTouSwitchesTheScheduleOff()
    {
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("load", "limit_home_load", true, ("22:00", "04:00", 3000, 90)), null, null);
        Assert.Contains(commands, c => c.Action == "SET_CHARGE_SCHEDULE" && (bool)c.Params["enabled"]! == false);
        Assert.False(expected.ScheduleEnabled);
        // nothing to switch off when it is already off
        (commands, _) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, State("charging", "limit_home_load"), null, null);
        Assert.DoesNotContain(commands, c => c.Action == "SET_CHARGE_SCHEDULE");
    }

    private static JsonElement WithDischarge(JsonElement state, bool enabled, params (string Start, string End, int W, int Soc)[] periods)
    {
        var o = System.Text.Json.Nodes.JsonNode.Parse(state.GetRawText())!.AsObject();
        o["discharge_schedule"] = new System.Text.Json.Nodes.JsonObject
        {
            ["enabled"] = enabled,
            ["periods"] = new System.Text.Json.Nodes.JsonArray(Enumerable.Range(0, 3).Select(i => (System.Text.Json.Nodes.JsonNode)new System.Text.Json.Nodes.JsonObject
            {
                ["period"] = i + 1,
                ["start_time"] = i < periods.Length ? periods[i].Start : "00:00", ["end_time"] = i < periods.Length ? periods[i].End : "00:00",
                ["discharge_power_w"] = i < periods.Length ? periods[i].W : 6000, ["stop_soc"] = i < periods.Length ? periods[i].Soc : 10,
            }).ToArray()),
        };
        return JsonDocument.Parse(o.ToJsonString()).RootElement;
    }

    [Fact]
    public void DischargeSlotsAreSentOnlyToDevicesThatSupportThem()
    {
        var today = new TouStrategy(Guid.NewGuid().ToString(), "T", TouRules.Days.ToList(),
            [new TouSlot(new(22, 0), new(4, 0), 90, 3000)], [new TouSlot(new(18, 0), new(21, 0), 20, 5000)]);

        // datalogger before 1.0.17: no discharge_schedule reported, no discharge command
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.Tou, State("load", "limit_home_load"), today, null);
        Assert.DoesNotContain(commands, c => c.Action == "SET_DISCHARGE_SCHEDULE");
        Assert.Null(expected.DischargeEnabled);
        Assert.False(ConfigMapping.SupportsDischarge(State("load", "limit_home_load")));

        var supported = WithDischarge(State("load", "limit_home_load"), false);
        Assert.True(ConfigMapping.SupportsDischarge(supported));
        (commands, expected) = ConfigMapping.PlanMode(OperatingModes.Tou, supported, today, null);
        var cmd = commands.Single(c => c.Action == "SET_DISCHARGE_SCHEDULE");
        Assert.True((bool)cmd.Params["enabled"]!);
        var periods = cmd.Params["periods"]!.AsArray();
        Assert.Equal(("18:00", "21:00", 5000, 20), ((string)periods[0]!["start_time"]!, (string)periods[0]!["end_time"]!, (int)periods[0]!["discharge_power_w"]!, (int)periods[0]!["stop_soc"]!));
        Assert.Equal("00:00", (string)periods[2]!["end_time"]!);
        // the enable flag is the last thing each schedule command sets, and the mode is written first
        Assert.Equal(["SET_WORK_MODE", "SET_CHARGE_SCHEDULE", "SET_DISCHARGE_SCHEDULE"], commands.Select(c => c.Action));

        var after = WithDischarge(State("load", "limit_home_load", true, ("22:00", "04:00", 3000, 90)), true, ("18:00", "21:00", 5000, 20));
        Assert.True(ConfigMapping.Matches(expected, after));
        Assert.False(ConfigMapping.Matches(expected, WithDischarge(State("load", "limit_home_load", true, ("22:00", "04:00", 3000, 90)), true, ("18:00", "21:00", 4000, 20))));
        Assert.False(ConfigMapping.Matches(expected, WithDischarge(State("load", "limit_home_load", true, ("22:00", "04:00", 3000, 90)), false, ("18:00", "21:00", 5000, 20))));
        // discharging on a schedule also reads as TOU
        Assert.Equal(OperatingModes.Tou, ConfigMapping.ReadMode(WithDischarge(State("load", "limit_home_load"), true, ("18:00", "21:00", 5000, 20)), false));
    }

    [Fact]
    public void LeavingTouSwitchesTimedDischargeOffAsWell()
    {
        var state = WithDischarge(State("load", "limit_home_load", true, ("22:00", "04:00", 3000, 90)), true, ("18:00", "21:00", 5000, 20));
        var (commands, expected) = ConfigMapping.PlanMode(OperatingModes.SelfConsumption, state, null, null);
        Assert.Contains(commands, c => c.Action == "SET_CHARGE_SCHEDULE" && (bool)c.Params["enabled"]! == false);
        Assert.Contains(commands, c => c.Action == "SET_DISCHARGE_SCHEDULE" && (bool)c.Params["enabled"]! == false);
        Assert.Equal((false, false), (expected.ScheduleEnabled, expected.DischargeEnabled));
        // a strategy with charge slots only: discharge is switched off, not left running
        var chargeOnly = new TouStrategy(Guid.NewGuid().ToString(), "T", TouRules.Days.ToList(), [new TouSlot(new(1, 0), new(2, 0), 90, 1000)], []);
        (commands, expected) = ConfigMapping.PlanSchedule(state, chargeOnly);
        Assert.Contains(commands, c => c.Action == "SET_DISCHARGE_SCHEDULE" && (bool)c.Params["enabled"]! == false);
        Assert.False(expected.DischargeEnabled);
    }

    [Fact]
    public void RestoreWritesBackEveryPeriod()
    {
        var old = WithDischarge(State("charging", "limit_ups_load", true, ("01:00", "03:00", 3000, 100)), true, ("18:00", "21:00", 4000, 20));
        var (commands, expected) = ConfigMapping.PlanRestore(old);
        Assert.Equal(["SET_WORK_MODE", "SET_CHARGE_SCHEDULE", "SET_DISCHARGE_SCHEDULE"], commands.Select(c => c.Action));
        Assert.Equal(("charging", "limit_ups_load"), ((string)commands[0].Params["pv_priority"]!, (string)commands[0].Params["grid_mode"]!));
        Assert.True((bool)commands[2].Params["enabled"]!);
        Assert.Equal(3, commands[2].Params["periods"]!.AsArray().Count);
        Assert.True(ConfigMapping.Matches(expected, old));

        // an enabled schedule with no period information cannot be re-enabled by the datalogger: it is not attempted
        var bare = JsonDocument.Parse("""{"pv_priority":"load","grid_mode":"limit_home_load","charge_schedule":{"enabled":true}}""").RootElement;
        (commands, _) = ConfigMapping.PlanRestore(bare);
        Assert.False((bool)commands.Single(c => c.Action == "SET_CHARGE_SCHEDULE").Params["enabled"]!);
        Assert.DoesNotContain(commands, c => c.Action == "SET_DISCHARGE_SCHEDULE");
    }

    [Fact]
    public void StoredTimesCarryOnlyHourAndMinute()
    {
        var json = JsonSerializer.Serialize(new TimeOfDay(4, 30), GgPower.Infrastructure.LiveJson.Options);
        Assert.Equal("""{"hour":4,"minute":30}""", json);
    }

    [Fact]
    public void VersionChangesWithAnythingTheUserCanChange()
    {
        var s = State("load", "limit_home_load");
        var v = ConfigMapping.Version("self_consumption", 10, "[]", s);
        Assert.StartsWith("v-", v);
        Assert.Equal(v, ConfigMapping.Version("self_consumption", 10, "[]", s));
        Assert.NotEqual(v, ConfigMapping.Version("tou", 10, "[]", s));
        Assert.NotEqual(v, ConfigMapping.Version("self_consumption", 20, "[]", s));
        Assert.NotEqual(v, ConfigMapping.Version("self_consumption", 10, "[{}]", s));
        Assert.NotEqual(v, ConfigMapping.Version("self_consumption", 10, "[]", State("load", "limit_home_load", true, ("01:00", "02:00", 1000, 80))));
    }

    [Fact]
    public void OwnersAreNotOfferedGridExport()
    {
        Assert.DoesNotContain(OperatingModes.MaxGridExport, OperatingModes.WritableFor(Roles.Owner));
        Assert.Contains(OperatingModes.MaxGridExport, OperatingModes.WritableFor(Roles.Distributor));
        Assert.Contains(OperatingModes.MaxGridExport, OperatingModes.WritableFor(Roles.Technician));
    }
}

public class PayloadTests
{
    private static readonly DateTimeOffset Now = new(2026, 10, 6, 3, 0, 0, TimeSpan.Zero);
    private static JsonElement J(string json) => JsonDocument.Parse(json).RootElement;

    [Fact]
    public void TelemetryKeepsMissingFieldsNullAndFlipsBatterySign()
    {
        var json = "{\"sn\":\"X\",\"ts\":" + Now.ToUnixTimeSeconds() + ",\"pv\":{\"p_total\":1500},\"grid\":{\"p_active\":-200},\"battery\":{\"p_flow\":-800,\"soc\":55}}";
        var t = Payloads.Telemetry(J(json), "X", Now)!;
        Assert.Equal(1500, t.PvW);
        Assert.Equal(-200, t.GridW);          // export stays negative
        Assert.Equal(800, t.BattW);           // firmware: negative = charging; here: positive = charging
        Assert.Equal(55, t.Soc);
        Assert.Null(t.LoadW);                 // absent is unknown, not zero
        Assert.Null(t.ImportToday);
        Assert.Equal(Now, t.Ts);
    }

    [Fact]
    public void DeviceClockOutsideTheWindowIsReplacedByReceiveTime()
    {
        Assert.Equal(Now, Payloads.SaneTs(0, Now));
        Assert.Equal(Now, Payloads.SaneTs(Now.AddDays(-2).ToUnixTimeSeconds(), Now));
        Assert.Equal(Now, Payloads.SaneTs(Now.AddHours(1).ToUnixTimeSeconds(), Now));
        Assert.Equal(Now, Payloads.SaneTs(null, Now));
        Assert.Equal(Now.AddSeconds(-30), Payloads.SaneTs(Now.AddSeconds(-30).ToUnixTimeSeconds(), Now));
    }

    [Fact]
    public void BatteryCurrentSignIsFlipped()
    {
        var b = Payloads.Battery(J("""{"sn":"X","soc":60,"voltage":51.2,"current":-10.5}"""), "X", Now)!;
        Assert.Equal(10.5, b.CurrentA);
        Assert.Null(b.Soh);
    }

    [Fact]
    public void AlarmAndStatusParsing()
    {
        var a = Payloads.Alarm(J("""{"alarm_id":"ALM-1","event_state":"triggered","error_code":"F024","severity":"critical","title":"T"}"""), "X", Now)!;
        Assert.True(a.Triggered);
        Assert.Equal("error", a.Severity);
        Assert.Equal("warning", Payloads.Alarm(J("""{"alarm_id":"A","event_state":"cleared","severity":"warning"}"""), "X", Now)!.Severity);
        Assert.Equal("info", Payloads.Alarm(J("""{"alarm_id":"A","event_state":"cleared","severity":"whatever"}"""), "X", Now)!.Severity);
        Assert.Null(Payloads.Alarm(J("""{"event_state":"triggered"}"""), "X", Now));
        Assert.Null(Payloads.Alarm(J("""{"alarm_id":"A","event_state":"maybe"}"""), "X", Now));

        Assert.True(Payloads.Status(J("""{"state":"online","model":"M"}"""), "X", Now)!.Online);
        Assert.False(Payloads.Status(J("""{"state":"offline","reason":"connection_lost"}"""), "X", Now)!.Online);
        Assert.Null(Payloads.Status(J("""{"state":"sleeping"}"""), "X", Now));
        Assert.Null(Payloads.Ack(J("""{"success":true}"""), "X"));
        Assert.True(Payloads.Ack(J("""{"cmd_id":"c","success":true,"code":200}"""), "X")!.Success);
    }

    [Theory]
    [InlineData("solar/ggp/inverter/SR-1/up/telemetry", "ggp", "SR-1", "telemetry")]
    [InlineData("solar/ggp/inverter/SR-1/up/config/state", "ggp", "SR-1", "config/state")]
    public void UpTopicParses(string topic, string tenant, string serial, string kind)
    {
        var t = UpTopic.Parse(topic)!.Value;
        Assert.Equal((tenant, serial, kind), (t.Tenant, t.Serial, t.Kind));
    }

    [Theory]
    [InlineData("solar/ggp/inverter/SR-1/down/command")]
    [InlineData("other/ggp/inverter/SR-1/up/telemetry")]
    [InlineData("solar/ggp/inverter/bad serial/up/telemetry")]
    [InlineData("solar/ggp/inverter/a'--/up/telemetry")]
    [InlineData("solar/ggp/inverter/SR-1/up")]
    public void UpTopicRejects(string topic) => Assert.Null(UpTopic.Parse(topic));
}

public class AccountRuleTests
{
    [Theory]
    [InlineData("0912345678", "0912345678")]
    [InlineData("+84912345678", "0912345678")]
    [InlineData("84912345678", "0912345678")]
    [InlineData("0912 345.678", "0912345678")]
    [InlineData(" User@Example.COM ", "user@example.com")]
    public void ParsesAccounts(string raw, string expected) => Assert.Equal(expected, AccountId.Parse(raw)!.Destination);

    [Theory]
    [InlineData("")]
    [InlineData("0212345678")]      // not a mobile prefix
    [InlineData("091234567")]
    [InlineData("user@")]
    [InlineData("a b@c.vn")]
    public void RejectsAccounts(string raw) => Assert.Null(AccountId.Parse(raw));

    [Fact]
    public void PasswordRule()
    {
        Assert.True(PasswordRules.IsValid("matkhau123"));
        Assert.False(PasswordRules.IsValid("short1"));
        Assert.False(PasswordRules.IsValid("onlyletters"));
        Assert.False(PasswordRules.IsValid("1234567890"));
        Assert.False(PasswordRules.IsValid(new string('a', 200) + "1"));
        Assert.False(PasswordRules.IsValid(null));
    }

    [Fact]
    public void PasswordHashing()
    {
        var hash = PasswordHasher.Hash("matkhau123");
        Assert.StartsWith("argon2id$", hash);
        Assert.True(PasswordHasher.Verify("matkhau123", hash));
        Assert.False(PasswordHasher.Verify("matkhau124", hash));
        Assert.NotEqual(hash, PasswordHasher.Hash("matkhau123"));   // salted
        Assert.False(PasswordHasher.Verify("x", "not-a-hash"));
        Assert.False(PasswordHasher.Verify("x", "argon2id$a$b$c$d$e"));
    }
}

public class BatteryOriginTests
{
    [Fact]
    public void ChargingSplitsBetweenPvAndGridAndDischargeIsProportional()
    {
        // 1 hour at 1000 W with 600 W of PV: 0.6 kWh from PV, 0.4 kWh from the grid.
        var (pv, grid) = BatteryOrigin.Step(0, 0, pvW: 600, battW: 1000, soc: null, capacityKwh: null, dtSeconds: 3600);
        Assert.Equal(0.6, pv, 6);
        Assert.Equal(0.4, grid, 6);
        // Discharging half of it keeps the 60/40 ratio.
        (pv, grid) = BatteryOrigin.Step(pv, grid, pvW: 0, battW: -500, soc: null, capacityKwh: null, dtSeconds: 3600);
        Assert.Equal(0.3, pv, 6);
        Assert.Equal(0.2, grid, 6);
    }

    [Fact]
    public void EstimateFollowsTheReportedStateOfCharge()
    {
        var (pv, grid) = BatteryOrigin.Step(3, 1, pvW: 0, battW: 0, soc: 50, capacityKwh: 16, dtSeconds: 5);
        Assert.Equal(8, pv + grid, 6);
        Assert.Equal(6, pv, 6);     // ratio kept
    }

    [Fact]
    public void NeverNegative()
    {
        var (pv, grid) = BatteryOrigin.Step(0.01, 0.01, pvW: 0, battW: -5000, soc: null, capacityKwh: null, dtSeconds: 3600);
        Assert.True(pv >= 0 && grid >= 0);
    }
}
