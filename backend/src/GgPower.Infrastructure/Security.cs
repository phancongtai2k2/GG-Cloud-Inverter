using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using Konscious.Security.Cryptography;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace GgPower.Infrastructure;

public sealed class SecurityOptions
{
    /// <summary>HMAC-SHA256 signing key, at least 32 bytes. Never in source control.</summary>
    public string JwtSigningKey { get; set; } = "";
    public string Issuer { get; set; } = "ggpower-backend";
    public string Audience { get; set; } = "ggpower-app";
    public int AccessTokenMinutes { get; set; } = 15;
    public int RefreshTokenDays { get; set; } = 30;
    public int OtpMinutes { get; set; } = 5;
    public int OtpMaxAttempts { get; set; } = 5;
    public int OtpResendSeconds { get; set; } = 60;
    public int OtpMaxPerHour { get; set; } = 5;
    public int LoginMaxFailures { get; set; } = 5;
    public int LoginLockMinutes { get; set; } = 15;
    /// <summary>Development only: the OTP is written to the server log instead of being sent.</summary>
    public bool LogOtpCodes { get; set; }
}

/// <summary>Argon2id (OWASP parameters: 19 MiB, 2 iterations, 1 lane). Format: argon2id$m$t$p$salt$hash.</summary>
public static class PasswordHasher
{
    private const int MemoryKb = 19456, Iterations = 2, Parallelism = 1, SaltBytes = 16, HashBytes = 32;

    public static string Hash(string password)
    {
        var salt = RandomNumberGenerator.GetBytes(SaltBytes);
        var hash = Compute(password, salt, MemoryKb, Iterations, Parallelism);
        return $"argon2id${MemoryKb}${Iterations}${Parallelism}${Convert.ToBase64String(salt)}${Convert.ToBase64String(hash)}";
    }

    public static bool Verify(string password, string stored)
    {
        var p = stored.Split('$');
        if (p.Length != 6 || p[0] != "argon2id") return false;
        if (!int.TryParse(p[1], out var m) || !int.TryParse(p[2], out var t) || !int.TryParse(p[3], out var par)) return false;
        byte[] salt, expected;
        try { salt = Convert.FromBase64String(p[4]); expected = Convert.FromBase64String(p[5]); }
        catch (FormatException) { return false; }
        var actual = Compute(password, salt, m, t, par);
        return CryptographicOperations.FixedTimeEquals(actual, expected);
    }

    /// <summary>Burns the same time as a real verification, so unknown accounts are not detectable by timing.</summary>
    public static void DummyVerify(string password) => Compute(password, new byte[SaltBytes], MemoryKb, Iterations, Parallelism);

    private static byte[] Compute(string password, byte[] salt, int memoryKb, int iterations, int parallelism)
    {
        using var a = new Argon2id(Encoding.UTF8.GetBytes(password))
        {
            Salt = salt, MemorySize = memoryKb, Iterations = iterations, DegreeOfParallelism = parallelism,
        };
        return a.GetBytes(HashBytes);
    }
}

public static class Secrets
{
    public static string NewToken(int bytes = 32) => Base64UrlEncoder.Encode(RandomNumberGenerator.GetBytes(bytes));

    public static byte[] Sha256(string value) => SHA256.HashData(Encoding.UTF8.GetBytes(value));

    public static string NewOtp() => RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
}

public sealed record AccessToken(string Value, DateTimeOffset ExpiresAt);

public sealed class TokenService
{
    private readonly SecurityOptions _o;
    private readonly SigningCredentials _credentials;
    public TokenValidationParameters ValidationParameters { get; }

    public TokenService(IOptions<SecurityOptions> options)
    {
        _o = options.Value;
        var keyBytes = Encoding.UTF8.GetBytes(_o.JwtSigningKey);
        if (keyBytes.Length < 32)
            throw new InvalidOperationException("Security:JwtSigningKey must be set to at least 32 bytes (user-secrets or environment).");
        var key = new SymmetricSecurityKey(keyBytes);
        _credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);
        ValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true, ValidIssuer = _o.Issuer,
            ValidateAudience = true, ValidAudience = _o.Audience,
            ValidateIssuerSigningKey = true, IssuerSigningKey = key,
            ValidateLifetime = true, ClockSkew = TimeSpan.FromSeconds(30),
            ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
            NameClaimType = "sub", RoleClaimType = "role",
        };
    }

    public AccessToken Issue(Guid userId, string role, bool writeAdmin, Guid sessionFamily, DateTimeOffset now)
    {
        var expires = now.AddMinutes(_o.AccessTokenMinutes);
        var claims = new List<Claim>
        {
            new("sub", userId.ToString()),
            new("role", role),
            new("sid", sessionFamily.ToString()),
            new(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString("N")),
        };
        if (writeAdmin) claims.Add(new Claim("write_admin", "1"));
        var jwt = new JwtSecurityToken(_o.Issuer, _o.Audience, claims, now.UtcDateTime, expires.UtcDateTime, _credentials);
        return new AccessToken(new JwtSecurityTokenHandler().WriteToken(jwt), expires);
    }
}
