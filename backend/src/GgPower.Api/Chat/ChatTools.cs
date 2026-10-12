using System.Globalization;
using System.Text.Json;
using Dapper;
using GgPower.Api.Endpoints;
using GgPower.Infrastructure;
using Microsoft.Extensions.Options;

namespace GgPower.Api.Chat;

/// <summary>Weather forecast for a place (Open-Meteo in production, a fake in tests).</summary>
public interface IWeatherSource
{
    Task<JsonElement> ForecastAsync(double latitude, double longitude, string timeZone, int days, CancellationToken ct);
}

public sealed class OpenMeteoWeather(HttpClient http, IOptions<ChatOptions> options) : IWeatherSource
{
    public async Task<JsonElement> ForecastAsync(double latitude, double longitude, string timeZone, int days, CancellationToken ct)
    {
        var inv = CultureInfo.InvariantCulture;
        var url = options.Value.WeatherUrl
            + $"?latitude={latitude.ToString("0.00", inv)}&longitude={longitude.ToString("0.00", inv)}"
            + "&current=temperature_2m,relative_humidity_2m,weather_code,cloud_cover,wind_speed_10m,precipitation"
            + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,"
            + "cloud_cover_mean,shortwave_radiation_sum,sunshine_duration,uv_index_max"
            + $"&timezone={Uri.EscapeDataString(timeZone)}&forecast_days={days}";
        using var res = await http.GetAsync(url, ct);
        res.EnsureSuccessStatusCode();
        await using var s = await res.Content.ReadAsStreamAsync(ct);
        using var doc = await JsonDocument.ParseAsync(s, cancellationToken: ct);
        return doc.RootElement.Clone();
    }
}

/// <summary>
/// What the assistant can look up. Every tool works on the one project the conversation belongs to, which the caller's
/// membership was checked for; no tool takes a project, device or user id, so the model cannot reach anyone else's data.
/// </summary>
public sealed class ChatTools(ILiveStore live, StatsService stats, Db db, TimeProvider clock, IWeatherSource weather, ILogger<ChatTools> log)
{
    private const int MaxResultChars = 24_000;

    public static readonly IReadOnlyList<ChatToolSpec> Specs =
    [
        new("get_project_info",
            "Thông tin cố định của dự án đang chọn: tên, loại công trình, công suất lắp đặt PV (kWp), ngày lắp đặt, múi giờ, " +
            "loại biểu giá điện, giá bán điện, số thiết bị, có toạ độ hay không.",
            new Dictionary<string, object>(), []),
        new("get_live_status",
            "Trạng thái hiện tại của hệ thống: trạng thái inverter, công suất PV / lưới / pin / tải (kW, lưới dương = mua, " +
            "pin dương = sạc), SOC pin, sản lượng hôm nay và hôm qua (kWh), CO2 tránh được hôm nay, chế độ vận hành, " +
            "số cảnh báo chưa xử lý.",
            new Dictionary<string, object>(), []),
        new("get_devices",
            "Danh sách thiết bị của dự án: inverter (tên, serial, trạng thái, công suất, nhiệt độ, firmware), datalogger " +
            "(online, cường độ tín hiệu), pin (SOC, SOH, trạng thái sạc / xả, điện áp, thời gian còn lại).",
            new Dictionary<string, object>(), []),
        new("get_energy_stats",
            "Thống kê năng lượng theo kỳ: sản lượng PV, điện tiêu thụ, mua / bán lưới, sạc / xả pin, tỷ lệ tự dùng, tiền " +
            "tiết kiệm, CO2. period=day cho từng mốc trong một ngày, month cho từng ngày trong tháng, year cho từng tháng trong năm.",
            new Dictionary<string, object>
            {
                ["period"] = new { type = "string", @enum = new[] { "day", "month", "year" } },
                ["date"] = new { type = "string", description = "Ngày bất kỳ trong kỳ cần xem, dạng yyyy-MM-dd (giờ địa phương của dự án)." },
            }, ["period", "date"]),
        new("get_alerts",
            "Cảnh báo / lỗi của thiết bị trong dự án, mới nhất trước: mức độ, mã, tiêu đề, mô tả, thời điểm bắt đầu / kết thúc, " +
            "số lần lặp lại, đã xử lý chưa.",
            new Dictionary<string, object>
            {
                ["only_open"] = new { type = "boolean", description = "true: chỉ cảnh báo đang còn (chưa kết thúc)." },
            }, []),
        new("get_weather",
            "Thời tiết tại vị trí dự án: hiện tại và dự báo theo ngày (nhiệt độ, mưa, mây, bức xạ mặt trời, số giờ nắng, UV). " +
            "Dùng để trả lời về thời tiết và ước đoán sản lượng PV các ngày tới.",
            new Dictionary<string, object>
            {
                ["days"] = new { type = "integer", minimum = 1, maximum = 7, description = "Số ngày dự báo, tính cả hôm nay (mặc định 3)." },
            }, []),
    ];

    /// <summary>Runs one tool call; failures come back as an error result the model can explain, never as an exception.</summary>
    public async Task<(string Result, bool IsError)> RunAsync(ProjectAccess a, string name, JsonElement? input, CancellationToken ct)
    {
        try
        {
            object result = name switch
            {
                "get_project_info" => ProjectInfo(a),
                "get_live_status" => await DataEndpoints.OverviewAsync(a, live, stats, db, clock, ct),
                "get_devices" => await DataEndpoints.DevicesAsync(a, live, db, clock, ct),
                "get_energy_stats" => await EnergyStatsAsync(a, input, ct),
                "get_alerts" => await AlertsAsync(a, input, ct),
                "get_weather" => await WeatherAsync(a, input, ct),
                _ => throw new ToolInputException($"Không có công cụ '{name}'."),
            };
            var json = JsonSerializer.Serialize(result, LiveJson.Options);
            return (json.Length <= MaxResultChars ? json : json[..MaxResultChars] + " …(cắt bớt)", false);
        }
        catch (ToolInputException e) { return (e.Message, true); }
        catch (Exception e) when (e is not OperationCanceledException)
        {
            log.LogWarning(e, "chat tool {Tool} failed", name);
            return ("Không lấy được dữ liệu lúc này.", true);
        }
    }

    private sealed class ToolInputException(string message) : Exception(message);

    private static object ProjectInfo(ProjectAccess a)
    {
        var p = a.Project;
        // The address and the owner are not passed on: the assistant does not need them.
        return new
        {
            name = p.Name, site_type = p.SiteType, pv_kwp = p.PvKwp, installed_on = DateOnly.FromDateTime(p.InstalledOn).ToString("yyyy-MM-dd"),
            time_zone = p.TimeZone, tariff_kind = p.TariffKind, tariff_fixed_vnd_per_kwh = p.TariffFixed, tariff_sell_vnd_per_kwh = p.TariffSell,
            device_count = a.Serials.Length, has_location = p.Latitude is not null && p.Longitude is not null,
        };
    }

    private async Task<object> EnergyStatsAsync(ProjectAccess a, JsonElement? input, CancellationToken ct)
    {
        var period = Str(input, "period");
        if (period is not ("day" or "month" or "year")) throw new ToolInputException("period phải là day, month hoặc year.");
        if (!DateOnly.TryParseExact(Str(input, "date"), "yyyy-MM-dd", out var day)) throw new ToolInputException("date phải có dạng yyyy-MM-dd.");
        var (body, _) = await stats.StatsAsync(a, period, day, ct);
        return body;
    }

    private async Task<object> AlertsAsync(ProjectAccess a, JsonElement? input, CancellationToken ct)
    {
        var onlyOpen = input is { ValueKind: JsonValueKind.Object } o && o.TryGetProperty("only_open", out var v) && v.ValueKind == JsonValueKind.True;
        await using var c = await db.OpenAsync(ct);
        var rows = await c.QueryAsync(
            $"SELECT severity, code, title, description, started_at, ended_at, resolved, occurrences FROM alerts WHERE serial = ANY(@serials) {(onlyOpen ? "AND ended_at IS NULL" : "")} ORDER BY started_at DESC LIMIT 30",
            new { serials = a.Serials });
        string Local(DateTime utc) => TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(utc, DateTimeKind.Utc), a.Zone).ToString("yyyy-MM-dd HH:mm");
        return rows.Select(r => new
        {
            severity = (string)r.severity, code = (string)r.code, title = (string)r.title, description = (string)r.description,
            started_at = Local((DateTime)r.started_at), ended_at = r.ended_at is DateTime e ? Local(e) : null,
            resolved = (bool)r.resolved, occurrences = (int)r.occurrences,
        }).ToList();
    }

    private async Task<object> WeatherAsync(ProjectAccess a, JsonElement? input, CancellationToken ct)
    {
        if (a.Project.Latitude is not { } lat || a.Project.Longitude is not { } lon)
            throw new ToolInputException("Dự án chưa có toạ độ nên không tra được thời tiết. Người dùng có thể thêm vị trí trong phần sửa dự án.");
        var days = input is { ValueKind: JsonValueKind.Object } o && o.TryGetProperty("days", out var v) && v.TryGetInt32(out var n) ? Math.Clamp(n, 1, 7) : 3;
        return await weather.ForecastAsync(lat, lon, a.Project.TimeZone, days, ct);
    }

    private static string? Str(JsonElement? input, string name) =>
        input is { ValueKind: JsonValueKind.Object } o && o.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;
}
