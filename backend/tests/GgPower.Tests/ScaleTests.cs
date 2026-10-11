using Dapper;
using GgPower.Infrastructure;
using Xunit;

namespace GgPower.Tests;

/// <summary>Checks for the scaling plan (backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx).</summary>
[Collection("app")]
public class ScaleTests(AppFixture app)
{
    // A1: the case-insensitive serial lookups the API runs must be able to use an index (no full-table scan).
    [Theory]
    [InlineData("SELECT pd.project_id FROM project_devices pd WHERE upper(pd.serial) = upper(@serial)", "project_devices_serial_upper_ix")]
    [InlineData("SELECT 1 FROM config_allowlist WHERE upper(serial) = upper(@serial)", "config_allowlist_serial_upper_ix")]
    [InlineData("SELECT serial FROM devices WHERE upper(serial) = @serial", "devices_serial_upper_ix")]
    [InlineData("SELECT id FROM config_commands WHERE upper(serial) = upper(@serial) ORDER BY created_at DESC LIMIT 20", "config_commands_serial_upper_ix")]
    public async Task SerialLookupsUseAnIndex(string query, string index)
    {
        await using var c = await app.Service<Db>().OpenAsync();
        await using var tx = await c.BeginTransactionAsync();
        // The test tables are tiny, where a sequential scan is cheaper; forbid it to see whether an index fits the query.
        await c.ExecuteAsync("SET LOCAL enable_seqscan = off", transaction: tx);
        var plan = string.Join("\n", await c.QueryAsync<string>("EXPLAIN " + query.Replace("@serial", "'TEST-ABC'"), transaction: tx));
        Assert.Contains(index, plan);
    }
}
