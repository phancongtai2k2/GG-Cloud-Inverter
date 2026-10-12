namespace GgPower.Infrastructure;

/// <summary>
/// What this instance does (plan B7), from the comma-separated setting <c>Roles</c>, default all three. Every instance
/// runs the same image.
/// <list type="bullet">
/// <item><c>api</c>: serves the REST API and WebSocket.</item>
/// <item><c>ingest</c>: receives device data over MQTT and writes it. Without it the MQTT link only sends commands.</item>
/// <item><c>jobs</c>: may run the periodic jobs (one leader per job, see <see cref="JobLeader"/>). Give it to the ingesting
/// instance: the roll-up and the offline alerts work from the data received there.</item>
/// </list>
/// </summary>
public sealed class InstanceRoles
{
    public const string Api = "api", Ingest = "ingest", Jobs = "jobs";

    public bool ServesApi { get; }
    public bool Ingests { get; }
    public bool RunsJobs { get; }

    public InstanceRoles(string? roles)
    {
        var set = (string.IsNullOrWhiteSpace(roles) ? $"{Api},{Ingest},{Jobs}" : roles)
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(r => r.ToLowerInvariant()).ToHashSet();
        if (set.Except([Api, Ingest, Jobs]).FirstOrDefault() is { } unknown)
            throw new InvalidOperationException($"Roles: unknown role '{unknown}' (api, ingest, jobs)");
        if (set.Count == 0) throw new InvalidOperationException("Roles: at least one of api, ingest, jobs");
        ServesApi = set.Contains(Api);
        Ingests = set.Contains(Ingest);
        RunsJobs = set.Contains(Jobs);
    }

    public override string ToString() =>
        string.Join(",", new[] { ServesApi ? Api : null, Ingests ? Ingest : null, RunsJobs ? Jobs : null }.OfType<string>());
}
