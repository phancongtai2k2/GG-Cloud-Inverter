using Dapper;
using GgPower.Domain;
using GgPower.Infrastructure;

namespace GgPower.Api.Endpoints;

public static class AuthEndpoints
{
    public sealed record LoginRequest(string? Account, string? Password);
    public sealed record RefreshRequest(string? RefreshToken);
    public sealed record RegisterRequest(string? Name, string? Email, string? Phone, string? Password);
    public sealed record PhoneRequest(string? Phone);
    public sealed record VerifyRegistrationRequest(string? Phone, string? Code);
    public sealed record AccountRequest(string? Account);
    public sealed record VerifyResetRequest(string? Account, string? Code);
    public sealed record ResetRequest(string? ResetToken, string? NewPassword);

    private static object Tokens(TokenPair t, DateTimeOffset now) => new
    {
        access_token = t.AccessToken,
        expires_in = (int)(t.AccessExpiresAt - now).TotalSeconds,
        refresh_token = t.RefreshToken,
        refresh_expires_in = (int)(t.RefreshExpiresAt - now).TotalSeconds,
        token_type = "Bearer",
    };

    private static IResult Fail<T>(AuthResult<T> r) => r.Error switch
    {
        AuthError.InvalidCredentials => Api.Error(401, "INVALID_CREDENTIALS"),
        AuthError.Locked => Api.Error(429, "ACCOUNT_LOCKED", new { RetryAfterSeconds = r.RetryAfterSeconds }),
        AuthError.NotVerified => Api.Error(403, "ACCOUNT_NOT_VERIFIED"),
        AuthError.EmailTaken => Api.Error(409, "EMAIL_TAKEN"),
        AuthError.PhoneTaken => Api.Error(409, "PHONE_TAKEN"),
        AuthError.TooManyRequests => Api.Error(429, "TOO_MANY_REQUESTS", new { RetryAfterSeconds = r.RetryAfterSeconds }),
        AuthError.CodeWrong => Api.Error(400, "CODE_WRONG", new { AttemptsLeft = r.AttemptsLeft }),
        AuthError.CodeExpired => Api.Error(410, "CODE_EXPIRED"),
        AuthError.CodeLocked => Api.Error(423, "CODE_LOCKED"),
        AuthError.TokenExpired => Api.Error(401, "TOKEN_EXPIRED"),
        _ => Api.Invalid(),
    };

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/auth").RequireRateLimiting("auth");

        g.MapPost("/login", async (LoginRequest r, AuthService auth, AuditLog audit, HttpContext ctx, TimeProvider clock, CancellationToken ct) =>
        {
            var res = await auth.LoginAsync(r.Account ?? "", r.Password ?? "", ctx.Request.Headers.UserAgent, ctx.ClientIp(), ct);
            if (!res.Ok)
            {
                await audit.WriteAsync(null, "login_failed", null, new { reason = res.Error.ToString() }, ctx.ClientIp(), ct);
                return Fail(res);
            }
            return Results.Json(Tokens(res.Value!, clock.GetUtcNow()));
        });

        g.MapPost("/refresh", async (RefreshRequest r, AuthService auth, HttpContext ctx, TimeProvider clock, CancellationToken ct) =>
        {
            var res = await auth.RefreshAsync(r.RefreshToken ?? "", ctx.Request.Headers.UserAgent, ctx.ClientIp(), ct);
            return res.Ok ? Results.Json(Tokens(res.Value!, clock.GetUtcNow())) : Fail(res);
        });

        g.MapPost("/logout", async (RefreshRequest r, AuthService auth, CancellationToken ct) =>
        {
            await auth.LogoutAsync(r.RefreshToken ?? "", ct);
            return Results.NoContent();
        });

        g.MapPost("/register", async (RegisterRequest r, AuthService auth, CancellationToken ct) =>
        {
            var res = await auth.RegisterAsync(r.Name ?? "", r.Email ?? "", r.Phone ?? "", r.Password ?? "", ct);
            return res.Ok ? Results.Json(new { code_sent = true }, statusCode: 202) : Fail(res);
        });

        g.MapPost("/register/resend", async (PhoneRequest r, AuthService auth, CancellationToken ct) =>
        {
            var res = await auth.ResendRegistrationCodeAsync(r.Phone ?? "", ct);
            return res.Ok ? Results.Json(new { code_sent = true }, statusCode: 202) : Fail(res);
        });

        g.MapPost("/register/verify", async (VerifyRegistrationRequest r, AuthService auth, HttpContext ctx, TimeProvider clock, CancellationToken ct) =>
        {
            var res = await auth.VerifyRegistrationAsync(r.Phone ?? "", r.Code ?? "", ctx.Request.Headers.UserAgent, ctx.ClientIp(), ct);
            return res.Ok ? Results.Json(Tokens(res.Value!, clock.GetUtcNow())) : Fail(res);
        });

        g.MapPost("/password/forgot", async (AccountRequest r, AuthService auth, CancellationToken ct) =>
        {
            var res = await auth.RequestResetCodeAsync(r.Account ?? "", ct);
            return res.Ok ? Results.Json(new { code_sent = true }, statusCode: 202) : Fail(res);
        });

        g.MapPost("/password/verify", async (VerifyResetRequest r, AuthService auth, CancellationToken ct) =>
        {
            var res = await auth.VerifyResetCodeAsync(r.Account ?? "", r.Code ?? "", ct);
            return res.Ok ? Results.Json(new { reset_token = res.Value }) : Fail(res);
        });

        g.MapPost("/password/reset", async (ResetRequest r, AuthService auth, AuditLog audit, HttpContext ctx, CancellationToken ct) =>
        {
            var res = await auth.ResetPasswordAsync(r.ResetToken ?? "", r.NewPassword ?? "", ct);
            if (!res.Ok) return Fail(res);
            await audit.WriteAsync(null, "password_reset", null, null, ctx.ClientIp(), ct);
            return Results.NoContent();
        });
    }
}

public static class ProfileEndpoints
{
    public sealed record Notifications(bool? DeviceAlerts, bool? DailyReport, bool? GgPowerNews);
    public sealed record PatchProfile(string? Name, string? Language, Notifications? Notifications);
    public sealed record DeletionRequest(string? Password);
    public sealed record PushTokenRequest(string? Token, string? Platform);

    private const int MaxAvatarBytes = 2 * 1024 * 1024;

    public static void Map(RouteGroupBuilder api)
    {
        var g = api.MapGroup("/me").RequireAuthorization();

        g.MapGet("", async (HttpContext ctx, Db db, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var u = await c.QuerySingleOrDefaultAsync(
                "SELECT id, name, email, phone, role, language, avatar_file, notif_device_alerts, notif_daily_report, notif_news FROM users WHERE id = @id AND status = 'active'",
                new { id = ctx.User.UserId() });
            if (u is null) return Api.Error(401, "UNAUTHENTICATED");
            return Api.Cached(ctx, new
            {
                id = (Guid)u.id, name = (string)u.name, email = (string)u.email, phone = (string)u.phone, role = (string)u.role,
                language = (string?)u.language,
                avatar_url = u.avatar_file is string f ? $"/api/v1/me/avatar?v={f[..8]}" : null,
                notifications = new { device_alerts = (bool)u.notif_device_alerts, daily_report = (bool)u.notif_daily_report, gg_power_news = (bool)u.notif_news },
            });
        });

        g.MapPatch("", async (PatchProfile r, HttpContext ctx, Db db, CancellationToken ct) =>
        {
            var name = r.Name?.Trim();
            if (name is { Length: 0 or > 80 }) return Api.Invalid(new[] { new FieldError("name", "NAME_INVALID") });
            if (r.Language is not (null or "vi" or "en")) return Api.Invalid(new[] { new FieldError("language", "VALUE_INVALID") });
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync(
                """
                UPDATE users SET name = COALESCE(@name, name), language = COALESCE(@language, language),
                       notif_device_alerts = COALESCE(@a, notif_device_alerts), notif_daily_report = COALESCE(@d, notif_daily_report), notif_news = COALESCE(@n, notif_news)
                WHERE id = @id
                """,
                new { name, language = r.Language, a = r.Notifications?.DeviceAlerts, d = r.Notifications?.DailyReport, n = r.Notifications?.GgPowerNews, id = ctx.User.UserId() });
            return Results.NoContent();
        });

        g.MapPut("/avatar", async (HttpContext ctx, Db db, IFileStore files, CancellationToken ct) =>
        {
            var data = await FileStore.ReadLimitedAsync(ctx.Request.Body, MaxAvatarBytes, ct);
            if (data is null) return Api.Error(413, "PAYLOAD_TOO_LARGE");
            if (FileStore.ImageType(data) is not { } type) return Api.Error(415, "UNSUPPORTED_MEDIA_TYPE");
            var name = await files.SaveAsync("avatars", data, type, ct);
            await using var c = await db.OpenAsync(ct);
            var old = await c.ExecuteScalarAsync<string?>("SELECT avatar_file FROM users WHERE id = @id", new { id = ctx.User.UserId() });
            await c.ExecuteAsync("UPDATE users SET avatar_file = @name WHERE id = @id", new { name, id = ctx.User.UserId() });
            await files.DeleteAsync("avatars", old, ct);
            return Results.Json(new { avatar_url = $"/api/v1/me/avatar?v={name[..8]}" });
        }).WithMetadata(new Microsoft.AspNetCore.Mvc.RequestSizeLimitAttribute(MaxAvatarBytes + 1024));

        g.MapDelete("/avatar", async (HttpContext ctx, Db db, IFileStore files, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var old = await c.ExecuteScalarAsync<string?>("SELECT avatar_file FROM users WHERE id = @id", new { id = ctx.User.UserId() });
            await c.ExecuteAsync("UPDATE users SET avatar_file = NULL WHERE id = @id", new { id = ctx.User.UserId() });
            await files.DeleteAsync("avatars", old, ct);
            return Results.NoContent();
        });

        g.MapGet("/avatar", async (HttpContext ctx, Db db, IFileStore files, CancellationToken ct) =>
        {
            await using var c = await db.OpenAsync(ct);
            var name = await c.ExecuteScalarAsync<string?>("SELECT avatar_file FROM users WHERE id = @id", new { id = ctx.User.UserId() });
            if (await files.OpenAsync("avatars", name, ct) is not { } file) return Api.NotFound();
            ctx.Response.Headers.CacheControl = "private, max-age=31536000, immutable";   // the URL changes with the file
            return Results.File(file, FileStore.ImageTypeOf(name!));
        });

        // Account deletion is a request handled by staff; the account is locked out at once.
        g.MapPost("/deletion", async (DeletionRequest r, HttpContext ctx, Db db, AuthService auth, AuditLog audit, CancellationToken ct) =>
        {
            if (!await auth.CheckPasswordAsync(ctx.User.UserId(), r.Password ?? "", ct)) return Api.Error(403, "WRONG_PASSWORD");
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync("UPDATE users SET status = 'deletion_requested' WHERE id = @id", new { id = ctx.User.UserId() });
            await c.ExecuteAsync("UPDATE sessions SET revoked_at = now() WHERE user_id = @id AND revoked_at IS NULL", new { id = ctx.User.UserId() });
            await audit.WriteAsync(ctx.User.UserId(), "account_deletion_requested", null, null, ctx.ClientIp(), ct);
            return Results.Json(new { requested = true }, statusCode: 202);
        }).RequireRateLimiting("auth");

        g.MapPut("/push-token", async (PushTokenRequest r, HttpContext ctx, Db db, CancellationToken ct) =>
        {
            if (r.Token is not { Length: > 10 and <= 4096 } || r.Platform is not ("android" or "ios")) return Api.Invalid();
            await using var c = await db.OpenAsync(ct);
            await c.ExecuteAsync(
                "INSERT INTO push_tokens (token, user_id, platform) VALUES (@Token, @id, @Platform) ON CONFLICT (token) DO UPDATE SET user_id = @id, platform = @Platform, updated_at = now()",
                new { r.Token, r.Platform, id = ctx.User.UserId() });
            return Results.NoContent();
        });
    }
}
