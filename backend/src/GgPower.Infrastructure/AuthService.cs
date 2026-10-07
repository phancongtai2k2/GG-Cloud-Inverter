using Dapper;
using GgPower.Domain;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Npgsql;

namespace GgPower.Infrastructure;

/// <summary>Delivers one-time codes. The development implementation only logs them.</summary>
public interface IOtpSender
{
    Task SendAsync(string destination, string code, string purpose, CancellationToken ct);
}

public sealed class LoggingOtpSender(ILogger<LoggingOtpSender> log, IOptions<SecurityOptions> o) : IOtpSender
{
    public Task SendAsync(string destination, string code, string purpose, CancellationToken ct)
    {
        if (o.Value.LogOtpCodes) log.LogWarning("DEV OTP for {Destination} ({Purpose}): {Code}", destination, purpose, code);
        else log.LogInformation("OTP requested for {Purpose}; no SMS/e-mail provider configured", purpose);
        return Task.CompletedTask;
    }
}

public sealed record TokenPair(string AccessToken, DateTimeOffset AccessExpiresAt, string RefreshToken, DateTimeOffset RefreshExpiresAt);

public enum AuthError
{
    None, InvalidCredentials, Locked, EmailTaken, PhoneTaken, Validation, TooManyRequests,
    CodeWrong, CodeExpired, CodeLocked, TokenExpired, NotVerified,
}

public sealed record AuthResult<T>(AuthError Error, T? Value = default, int AttemptsLeft = 0, int RetryAfterSeconds = 0)
{
    public bool Ok => Error == AuthError.None;
    public static AuthResult<T> Fail(AuthError e, int attemptsLeft = 0, int retryAfter = 0) => new(e, default, attemptsLeft, retryAfter);
    public static AuthResult<T> Success(T v) => new(AuthError.None, v);
}

public sealed class AuthService(Db db, TokenService tokens, IOtpSender otp, IOptions<SecurityOptions> options, TimeProvider clock)
{
    private readonly SecurityOptions _o = options.Value;

    // Read models are plain classes with DateTime members: that is what Npgsql hands to Dapper.
    private sealed class UserRow
    {
        public Guid Id { get; set; }
        public string PasswordHash { get; set; } = "";
        public string Role { get; set; } = "";
        public bool WriteAdmin { get; set; }
        public string Status { get; set; } = "";
        public int FailedLogins { get; set; }
        public DateTime? LockedUntil { get; set; }
    }

    private sealed class SessionRow
    {
        public Guid Id { get; set; }
        public Guid UserId { get; set; }
        public Guid FamilyId { get; set; }
        public DateTime ExpiresAt { get; set; }
        public DateTime? RotatedAt { get; set; }
        public DateTime? RevokedAt { get; set; }
    }

    private sealed class OtpRow
    {
        public Guid Id { get; set; }
        public Guid? UserId { get; set; }
        public byte[] CodeHash { get; set; } = [];
        public int Attempts { get; set; }
        public DateTime ExpiresAt { get; set; }
    }

    // ---------- login / tokens ----------

    public async Task<AuthResult<TokenPair>> LoginAsync(string account, string password, string? userAgent, string? ip, CancellationToken ct)
    {
        var id = AccountId.Parse(account);
        if (id is null || string.IsNullOrEmpty(password) || password.Length > PasswordRules.MaxLength)
        {
            PasswordHasher.DummyVerify(password ?? "");
            return AuthResult<TokenPair>.Fail(AuthError.InvalidCredentials);
        }

        await using var c = await db.OpenAsync(ct);
        var u = await FindUserAsync(c, id);
        var now = clock.GetUtcNow();
        if (u is null)
        {
            PasswordHasher.DummyVerify(password);
            return AuthResult<TokenPair>.Fail(AuthError.InvalidCredentials);
        }
        if (u.LockedUntil is { } until && until > now.UtcDateTime)
            return AuthResult<TokenPair>.Fail(AuthError.Locked, retryAfter: (int)Math.Ceiling((until - now.UtcDateTime).TotalSeconds));

        if (!PasswordHasher.Verify(password, u.PasswordHash))
        {
            var failures = u.FailedLogins + 1;
            var lockUntil = failures >= _o.LoginMaxFailures ? now.AddMinutes(_o.LoginLockMinutes) : (DateTimeOffset?)null;
            await c.ExecuteAsync("UPDATE users SET failed_logins = @f, locked_until = @l WHERE id = @id",
                new { f = lockUntil is null ? failures : 0, l = lockUntil, id = u.Id });
            return AuthResult<TokenPair>.Fail(AuthError.InvalidCredentials);
        }
        if (u.Status == "pending") return AuthResult<TokenPair>.Fail(AuthError.NotVerified);
        if (u.Status != "active") return AuthResult<TokenPair>.Fail(AuthError.InvalidCredentials);

        await c.ExecuteAsync("UPDATE users SET failed_logins = 0, locked_until = NULL WHERE id = @id", new { id = u.Id });
        return AuthResult<TokenPair>.Success(await NewSessionAsync(c, null, u, Guid.NewGuid(), userAgent, ip, now));
    }

    /// <summary>Rotates the refresh token. Re-use of an already rotated token revokes the whole family.</summary>
    public async Task<AuthResult<TokenPair>> RefreshAsync(string refreshToken, string? userAgent, string? ip, CancellationToken ct)
    {
        if (string.IsNullOrEmpty(refreshToken) || refreshToken.Length > 200) return AuthResult<TokenPair>.Fail(AuthError.TokenExpired);
        await using var c = await db.OpenAsync(ct);
        await using var tx = await c.BeginTransactionAsync(ct);
        var now = clock.GetUtcNow();
        var s = await c.QuerySingleOrDefaultAsync<SessionRow>(
            "SELECT id, user_id, family_id, expires_at, rotated_at, revoked_at FROM sessions WHERE refresh_hash = @h FOR UPDATE",
            new { h = Secrets.Sha256(refreshToken) }, tx);
        if (s is null) return AuthResult<TokenPair>.Fail(AuthError.TokenExpired);

        if (s.RotatedAt is not null || s.RevokedAt is not null)
        {
            // A token that was already exchanged is being presented again: assume it was stolen.
            await c.ExecuteAsync("UPDATE sessions SET revoked_at = @now WHERE family_id = @f AND revoked_at IS NULL", new { now, f = s.FamilyId }, tx);
            await tx.CommitAsync(ct);
            return AuthResult<TokenPair>.Fail(AuthError.TokenExpired);
        }
        if (s.ExpiresAt <= now.UtcDateTime) return AuthResult<TokenPair>.Fail(AuthError.TokenExpired);

        var u = await c.QuerySingleOrDefaultAsync<UserRow>(
            "SELECT id, password_hash, role, write_admin, status, failed_logins, locked_until FROM users WHERE id = @id", new { id = s.UserId }, tx);
        if (u is null || u.Status != "active") return AuthResult<TokenPair>.Fail(AuthError.TokenExpired);

        await c.ExecuteAsync("UPDATE sessions SET rotated_at = @now WHERE id = @id", new { now, id = s.Id }, tx);
        var pair = await NewSessionAsync(c, tx, u, s.FamilyId, userAgent, ip, now);
        await tx.CommitAsync(ct);
        return AuthResult<TokenPair>.Success(pair);
    }

    public async Task LogoutAsync(string refreshToken, CancellationToken ct)
    {
        if (string.IsNullOrEmpty(refreshToken) || refreshToken.Length > 200) return;
        await using var c = await db.OpenAsync(ct);
        await c.ExecuteAsync(
            "UPDATE sessions SET revoked_at = now() WHERE revoked_at IS NULL AND family_id = (SELECT family_id FROM sessions WHERE refresh_hash = @h)",
            new { h = Secrets.Sha256(refreshToken) });
    }

    /// <summary>What a request is allowed to rely on right now; the role is the user's current one, not the one in the token.</summary>
    public sealed record SessionState(string Role, bool WriteAdmin);

    /// <summary>Null when the session was signed out, revoked or expired, or the account is no longer active.</summary>
    public async Task<SessionState?> SessionStateAsync(Guid familyId, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var row = await c.QueryFirstOrDefaultAsync<UserRow>(
            """
            SELECT u.role, u.write_admin FROM sessions s JOIN users u ON u.id = s.user_id
            WHERE s.family_id = @familyId AND s.revoked_at IS NULL AND s.rotated_at IS NULL AND s.expires_at > now() AND u.status = 'active'
            LIMIT 1
            """, new { familyId });
        return row is null ? null : new SessionState(row.Role, row.WriteAdmin);
    }

    private async Task<TokenPair> NewSessionAsync(NpgsqlConnection c, NpgsqlTransaction? tx, UserRow u, Guid family, string? userAgent, string? ip, DateTimeOffset now)
    {
        var refresh = Secrets.NewToken();
        var refreshExpires = now.AddDays(_o.RefreshTokenDays);
        await c.ExecuteAsync(
            "INSERT INTO sessions (id, user_id, family_id, refresh_hash, created_at, expires_at, user_agent, ip) VALUES (@id, @uid, @family, @h, @now, @exp, @ua, @ip)",
            new { id = Guid.NewGuid(), uid = u.Id, family, h = Secrets.Sha256(refresh), now, exp = refreshExpires, ua = Trunc(userAgent, 200), ip }, tx);
        var access = tokens.Issue(u.Id, u.Role, u.WriteAdmin, family, now);
        return new TokenPair(access.Value, access.ExpiresAt, refresh, refreshExpires);
    }

    // ---------- registration ----------

    public async Task<AuthResult<bool>> RegisterAsync(string name, string email, string phone, string password, CancellationToken ct)
    {
        name = (name ?? "").Trim();
        if (name.Length is 0 or > 80 || AccountId.Parse(email) is not AccountId.Email e || AccountId.Parse(phone) is not AccountId.Phone p || !PasswordRules.IsValid(password))
            return AuthResult<bool>.Fail(AuthError.Validation);

        await using var c = await db.OpenAsync(ct);
        // An unverified registration may be repeated (the user never got the code): replace it.
        await c.ExecuteAsync("DELETE FROM users WHERE status = 'pending' AND (email = @email OR phone = @phone)", new { email = e.Value, phone = p.Value });
        if (await c.ExecuteScalarAsync<bool>("SELECT EXISTS (SELECT 1 FROM users WHERE email = @v)", new { v = e.Value }))
            return AuthResult<bool>.Fail(AuthError.EmailTaken);
        if (await c.ExecuteScalarAsync<bool>("SELECT EXISTS (SELECT 1 FROM users WHERE phone = @v)", new { v = p.Value }))
            return AuthResult<bool>.Fail(AuthError.PhoneTaken);

        var id = Guid.NewGuid();
        try
        {
            await c.ExecuteAsync(
                "INSERT INTO users (id, name, email, phone, password_hash, status) VALUES (@id, @name, @email, @phone, @hash, 'pending')",
                new { id, name, email = e.Value, phone = p.Value, hash = PasswordHasher.Hash(password) });
        }
        catch (PostgresException ex) when (ex.SqlState == PostgresErrorCodes.UniqueViolation)
        {
            return AuthResult<bool>.Fail(ex.ConstraintName == "users_phone_uq" ? AuthError.PhoneTaken : AuthError.EmailTaken);
        }
        var sent = await IssueOtpAsync(c, "register", p.Value, id, ct);
        return sent == AuthError.None ? AuthResult<bool>.Success(true) : AuthResult<bool>.Fail(sent);
    }

    public async Task<AuthResult<bool>> ResendRegistrationCodeAsync(string phone, CancellationToken ct)
    {
        if (AccountId.Parse(phone) is not AccountId.Phone p) return AuthResult<bool>.Fail(AuthError.Validation);
        await using var c = await db.OpenAsync(ct);
        var id = await c.ExecuteScalarAsync<Guid?>("SELECT id FROM users WHERE phone = @v AND status = 'pending'", new { v = p.Value });
        if (id is null) return AuthResult<bool>.Success(true);   // do not reveal whether the number is registered
        var e = await IssueOtpAsync(c, "register", p.Value, id, ct);
        return e == AuthError.None ? AuthResult<bool>.Success(true) : AuthResult<bool>.Fail(e, retryAfter: _o.OtpResendSeconds);
    }

    /// <summary>Confirms the phone number, activates the account and signs the user in.</summary>
    public async Task<AuthResult<TokenPair>> VerifyRegistrationAsync(string phone, string code, string? userAgent, string? ip, CancellationToken ct)
    {
        if (AccountId.Parse(phone) is not AccountId.Phone p) return AuthResult<TokenPair>.Fail(AuthError.Validation);
        await using var c = await db.OpenAsync(ct);
        var (err, left, userId) = await CheckOtpAsync(c, "register", p.Value, code, ct);
        if (err != AuthError.None) return AuthResult<TokenPair>.Fail(err, left);
        await c.ExecuteAsync("UPDATE users SET status = 'active' WHERE id = @id AND status = 'pending'", new { id = userId });
        var u = await c.QuerySingleAsync<UserRow>(
            "SELECT id, password_hash, role, write_admin, status, failed_logins, locked_until FROM users WHERE id = @id", new { id = userId });
        return AuthResult<TokenPair>.Success(await NewSessionAsync(c, null, u, Guid.NewGuid(), userAgent, ip, clock.GetUtcNow()));
    }

    // ---------- password reset ----------

    public async Task<AuthResult<bool>> RequestResetCodeAsync(string account, CancellationToken ct)
    {
        var id = AccountId.Parse(account);
        if (id is null) return AuthResult<bool>.Fail(AuthError.Validation);
        await using var c = await db.OpenAsync(ct);
        var u = await FindUserAsync(c, id);
        // Unknown accounts get the same answer and are rate limited the same way.
        var e = await IssueOtpAsync(c, "reset", id.Destination, u is { Status: "active" } ? u.Id : null, ct);
        return e == AuthError.None ? AuthResult<bool>.Success(true) : AuthResult<bool>.Fail(e, retryAfter: _o.OtpResendSeconds);
    }

    public async Task<AuthResult<string>> VerifyResetCodeAsync(string account, string code, CancellationToken ct)
    {
        var id = AccountId.Parse(account);
        if (id is null) return AuthResult<string>.Fail(AuthError.Validation);
        await using var c = await db.OpenAsync(ct);
        var (err, left, userId) = await CheckOtpAsync(c, "reset", id.Destination, code, ct);
        if (err != AuthError.None) return AuthResult<string>.Fail(err, left);
        if (userId is null) return AuthResult<string>.Fail(AuthError.CodeWrong, 0);
        var token = Secrets.NewToken();
        await c.ExecuteAsync("INSERT INTO reset_tokens (token_hash, user_id, expires_at) VALUES (@h, @u, @exp)",
            new { h = Secrets.Sha256(token), u = userId, exp = clock.GetUtcNow().AddMinutes(10) });
        return AuthResult<string>.Success(token);
    }

    public async Task<AuthResult<bool>> ResetPasswordAsync(string token, string newPassword, CancellationToken ct)
    {
        if (!PasswordRules.IsValid(newPassword)) return AuthResult<bool>.Fail(AuthError.Validation);
        if (string.IsNullOrEmpty(token) || token.Length > 200) return AuthResult<bool>.Fail(AuthError.TokenExpired);
        await using var c = await db.OpenAsync(ct);
        await using var tx = await c.BeginTransactionAsync(ct);
        var userId = await c.ExecuteScalarAsync<Guid?>(
            "UPDATE reset_tokens SET used_at = now() WHERE token_hash = @h AND used_at IS NULL AND expires_at > now() RETURNING user_id",
            new { h = Secrets.Sha256(token) }, tx);
        if (userId is null) return AuthResult<bool>.Fail(AuthError.TokenExpired);
        await c.ExecuteAsync("UPDATE users SET password_hash = @hash, failed_logins = 0, locked_until = NULL WHERE id = @id",
            new { hash = PasswordHasher.Hash(newPassword), id = userId }, tx);
        // A password change signs every device out.
        await c.ExecuteAsync("UPDATE sessions SET revoked_at = now() WHERE user_id = @id AND revoked_at IS NULL", new { id = userId }, tx);
        await tx.CommitAsync(ct);
        return AuthResult<bool>.Success(true);
    }

    public async Task<bool> CheckPasswordAsync(Guid userId, string password, CancellationToken ct)
    {
        await using var c = await db.OpenAsync(ct);
        var hash = await c.ExecuteScalarAsync<string?>("SELECT password_hash FROM users WHERE id = @userId", new { userId });
        if (hash is null || string.IsNullOrEmpty(password) || password.Length > PasswordRules.MaxLength) return false;
        return PasswordHasher.Verify(password, hash);
    }

    // ---------- one-time codes ----------

    private async Task<AuthError> IssueOtpAsync(NpgsqlConnection c, string purpose, string destination, Guid? userId, CancellationToken ct)
    {
        var now = clock.GetUtcNow();
        var recent = (await c.QueryAsync<DateTime>(
            "SELECT created_at FROM otp_codes WHERE purpose = @purpose AND destination = @destination AND created_at > @since ORDER BY created_at DESC",
            new { purpose, destination, since = now.AddHours(-1) })).ToList();
        if (recent.Count >= _o.OtpMaxPerHour) return AuthError.TooManyRequests;
        if (recent.Count > 0 && (now.UtcDateTime - recent[0]).TotalSeconds < _o.OtpResendSeconds) return AuthError.TooManyRequests;

        var code = Secrets.NewOtp();
        await c.ExecuteAsync("UPDATE otp_codes SET consumed_at = @now WHERE purpose = @purpose AND destination = @destination AND consumed_at IS NULL",
            new { now, purpose, destination });
        var id = Guid.NewGuid();
        await c.ExecuteAsync(
            "INSERT INTO otp_codes (id, purpose, destination, user_id, code_hash, created_at, expires_at) VALUES (@id, @purpose, @destination, @userId, @h, @now, @exp)",
            new { id, purpose, destination, userId, h = OtpHash(id, code), now, exp = now.AddMinutes(_o.OtpMinutes) });
        if (userId is not null) await otp.SendAsync(destination, code, purpose, ct);
        return AuthError.None;
    }

    private async Task<(AuthError Error, int AttemptsLeft, Guid? UserId)> CheckOtpAsync(NpgsqlConnection c, string purpose, string destination, string code, CancellationToken ct)
    {
        code = (code ?? "").Trim();
        var now = clock.GetUtcNow();
        await using var tx = await c.BeginTransactionAsync(ct);
        var row = await c.QuerySingleOrDefaultAsync<OtpRow>(
            "SELECT id, user_id, code_hash, attempts, expires_at FROM otp_codes WHERE purpose = @purpose AND destination = @destination AND consumed_at IS NULL ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
            new { purpose, destination }, tx);
        if (row is null || row.ExpiresAt <= now.UtcDateTime) return (AuthError.CodeExpired, 0, null);
        if (row.Attempts >= _o.OtpMaxAttempts) return (AuthError.CodeLocked, 0, null);

        var ok = code.Length == 6 && System.Security.Cryptography.CryptographicOperations.FixedTimeEquals(OtpHash(row.Id, code), row.CodeHash);
        if (!ok)
        {
            var attempts = row.Attempts + 1;
            await c.ExecuteAsync("UPDATE otp_codes SET attempts = @attempts WHERE id = @id", new { attempts, id = row.Id }, tx);
            await tx.CommitAsync(ct);
            var left = _o.OtpMaxAttempts - attempts;
            return (left <= 0 ? AuthError.CodeLocked : AuthError.CodeWrong, Math.Max(left, 0), null);
        }
        await c.ExecuteAsync("UPDATE otp_codes SET consumed_at = @now WHERE id = @id", new { now, id = row.Id }, tx);
        await tx.CommitAsync(ct);
        return (AuthError.None, 0, row.UserId);
    }

    // The code is hashed together with its row id, so equal codes do not produce equal hashes.
    private static byte[] OtpHash(Guid id, string code) => Secrets.Sha256($"{id:N}:{code}");

    private static async Task<UserRow?> FindUserAsync(NpgsqlConnection c, AccountId id) =>
        await c.QuerySingleOrDefaultAsync<UserRow>(
            $"SELECT id, password_hash, role, write_admin, status, failed_logins, locked_until FROM users WHERE {(id is AccountId.Email ? "email" : "phone")} = @v",
            new { v = id.Destination });

    private static string? Trunc(string? s, int n) => s is null ? null : s.Length <= n ? s : s[..n];
}
