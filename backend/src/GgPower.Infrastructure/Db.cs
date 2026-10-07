using System.Reflection;
using Dapper;
using Microsoft.Extensions.Logging;
using Npgsql;

namespace GgPower.Infrastructure;

/// <summary>Connection factory. All SQL in this code base is parameterised (Dapper / Npgsql).</summary>
public sealed class Db : IAsyncDisposable
{
    private readonly NpgsqlDataSource _source;

    static Db()
    {
        DefaultTypeMap.MatchNamesWithUnderscores = true;
        SqlMapper.AddTypeHandler(new DateOnlyHandler());
    }

    public Db(string connectionString)
    {
        var b = new NpgsqlDataSourceBuilder(connectionString);
        _source = b.Build();
    }

    public NpgsqlDataSource Source => _source;

    public async Task<NpgsqlConnection> OpenAsync(CancellationToken ct = default) =>
        await _source.OpenConnectionAsync(ct);

    public ValueTask DisposeAsync() => _source.DisposeAsync();

    private sealed class DateOnlyHandler : SqlMapper.TypeHandler<DateOnly>
    {
        public override void SetValue(System.Data.IDbDataParameter p, DateOnly v) => p.Value = v;
        public override DateOnly Parse(object v) => v is DateOnly d ? d : DateOnly.FromDateTime((DateTime)v);
    }
}

/// <summary>Applies the embedded Sql/NNN_*.sql scripts in order, each in its own transaction.</summary>
public sealed class Migrator(Db db, ILogger<Migrator> log)
{
    public async Task MigrateAsync(CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct);
        // One migrator at a time (several instances may start together).
        await c.ExecuteAsync("SELECT pg_advisory_lock(823400117)");
        try
        {
            await c.ExecuteAsync(
                "CREATE TABLE IF NOT EXISTS schema_migrations (name text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())");
            var done = (await c.QueryAsync<string>("SELECT name FROM schema_migrations")).ToHashSet();
            var asm = Assembly.GetExecutingAssembly();
            foreach (var res in asm.GetManifestResourceNames().Where(n => n.EndsWith(".sql")).OrderBy(n => n))
            {
                var name = res[(res.IndexOf(".Sql.", StringComparison.Ordinal) + 5)..];
                if (done.Contains(name)) continue;
                using var reader = new StreamReader(asm.GetManifestResourceStream(res)!);
                var sql = await reader.ReadToEndAsync(ct);
                await using var tx = await c.BeginTransactionAsync(ct);
                await c.ExecuteAsync(sql, transaction: tx);
                await c.ExecuteAsync("INSERT INTO schema_migrations (name) VALUES (@name)", new { name }, tx);
                await tx.CommitAsync(ct);
                log.LogInformation("applied migration {Name}", name);
            }
        }
        finally
        {
            await c.ExecuteAsync("SELECT pg_advisory_unlock(823400117)");
        }
    }
}

/// <summary>Monthly partitions of the raw tables: creates the ones ahead, drops the expired ones.</summary>
public sealed class PartitionMaintenance(Db db, ILogger<PartitionMaintenance> log)
{
    private static readonly string[] Tables = ["telemetry_raw", "battery_raw"];

    public async Task RunAsync(int retentionDays, DateTimeOffset now, CancellationToken ct = default)
    {
        await using var c = await db.OpenAsync(ct);
        var first = new DateTime(now.UtcDateTime.Year, now.UtcDateTime.Month, 1, 0, 0, 0, DateTimeKind.Utc);
        foreach (var table in Tables)
        {
            for (var i = -1; i <= 2; i++)
            {
                var from = first.AddMonths(i);
                var to = from.AddMonths(1);
                var name = $"{table}_{from:yyyyMM}";
                // Identifiers are built from constants and a date, never from input.
                await c.ExecuteAsync(
                    $"CREATE TABLE IF NOT EXISTS {name} PARTITION OF {table} FOR VALUES FROM ('{from:yyyy-MM-dd}') TO ('{to:yyyy-MM-dd}')");
            }

            var cutoff = now.UtcDateTime.AddDays(-retentionDays);
            var parts = await c.QueryAsync<string>(
                "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = @table",
                new { table });
            foreach (var p in parts)
            {
                var suffix = p[(table.Length + 1)..];
                if (suffix.Length != 6 || !int.TryParse(suffix[..4], out var y) || !int.TryParse(suffix[4..], out var m)) continue;
                var end = new DateTime(y, m, 1, 0, 0, 0, DateTimeKind.Utc).AddMonths(1);
                if (end >= cutoff) continue;
                await c.ExecuteAsync($"DROP TABLE IF EXISTS {p}");
                log.LogInformation("dropped expired partition {Partition}", p);
            }
        }
    }
}
