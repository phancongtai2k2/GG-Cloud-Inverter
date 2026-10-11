using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;
using YamlDotNet.RepresentationModel;

namespace GgPower.Tests;

/// <summary>
/// Keeps <c>contract/openapi.yaml</c> (the contract the Android app is built against) in step with the
/// routes this backend actually maps. Adding, removing or renaming an endpoint without editing the
/// contract fails here; record the change in <c>contract/CHANGES.md</c> as well.
/// </summary>
[Collection("app")]
public partial class ContractTests(AppFixture app)
{
    private static readonly string[] Methods = ["get", "post", "put", "patch", "delete"];

    private static YamlMappingNode LoadContract()
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir is not null; dir = dir.Parent)
        {
            var path = Path.Combine(dir.FullName, "contract", "openapi.yaml");
            if (!File.Exists(path)) continue;
            var yaml = new YamlStream();
            using var reader = File.OpenText(path);
            try { yaml.Load(reader); }
            catch (YamlDotNet.Core.YamlException ex) { throw new InvalidDataException($"{path} ({ex.Start}): {ex.Message}", ex); }
            return (YamlMappingNode)yaml.Documents[0].RootNode;
        }
        throw new FileNotFoundException("contract/openapi.yaml not found above the test output directory");
    }

    [GeneratedRegex(@"\{(\w+):[^}]+\}")]
    private static partial Regex Constraint();

    [Fact]
    public void EveryMappedRouteIsInTheContractAndViceVersa()
    {
        var paths = (YamlMappingNode)LoadContract()["paths"];
        var documented = paths.Children
            .SelectMany(p => ((YamlMappingNode)p.Value).Children.Keys.Select(k => k.ToString())
                .Where(Methods.Contains).Select(m => $"{m.ToUpperInvariant()} {p.Key}"))
            .ToHashSet();

        // The WebSocket endpoint has no HTTP method metadata; it is described in contract/realtime.md.
        var mapped = app.Service<EndpointDataSource>().Endpoints.OfType<RouteEndpoint>()
            .SelectMany(e => (e.Metadata.GetMetadata<IHttpMethodMetadata>()?.HttpMethods ?? []).Select(m =>
                $"{m} {Constraint().Replace(e.RoutePattern.RawText!.TrimEnd('/'), "{$1}")}"))
            .ToHashSet();

        Assert.Empty(mapped.Except(documented).Order());
        Assert.Empty(documented.Except(mapped).Order());
    }

    [Fact]
    public void EveryReferenceResolves()
    {
        var root = LoadContract();
        var components = (YamlMappingNode)root["components"];
        var refs = new List<string>();
        void Walk(YamlNode n)
        {
            switch (n)
            {
                case YamlMappingNode m:
                    foreach (var (k, v) in m.Children)
                    {
                        if (k.ToString() == "$ref") refs.Add(v.ToString());
                        else Walk(v);
                    }
                    break;
                case YamlSequenceNode s:
                    foreach (var c in s.Children) Walk(c);
                    break;
            }
        }
        Walk(root);
        Assert.NotEmpty(refs);
        var broken = refs.Distinct().Where(r =>
        {
            var parts = r.Split('/');   // #/components/<section>/<name>
            return parts is not ["#", "components", var section, var name]
                || !components.Children.TryGetValue(section, out var s)
                || !((YamlMappingNode)s).Children.ContainsKey(name);
        });
        Assert.Empty(broken);
    }
}
