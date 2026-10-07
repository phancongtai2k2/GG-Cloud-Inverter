using System.Text.RegularExpressions;

namespace GgPower.Domain;

/// <summary>Account identifier rules, identical to the app's AccountInput.parse.</summary>
public abstract record AccountId
{
    public sealed record Email(string Value) : AccountId;
    public sealed record Phone(string Value) : AccountId;

    private static readonly Regex EmailRx = new(@"^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$", RegexOptions.Compiled);
    private static readonly Regex Separators = new(@"[\s.\-]", RegexOptions.Compiled);
    private static readonly Regex VnMobile = new(@"^0[35789]\d{8}$", RegexOptions.Compiled);

    public static AccountId? Parse(string? raw)
    {
        var text = (raw ?? "").Trim();
        if (text.Length == 0 || text.Length > 254) return null;
        if (text.Contains('@')) return EmailRx.IsMatch(text) ? new Email(text.ToLowerInvariant()) : null;
        var digits = Separators.Replace(text, "");
        var local = digits.StartsWith("+84") ? "0" + digits[3..]
            : digits.StartsWith("84") && digits.Length == 11 ? "0" + digits[2..]
            : digits;
        return VnMobile.IsMatch(local) ? new Phone(local) : null;
    }

    public string Destination => this switch { Email e => e.Value, Phone p => p.Value, _ => "" };
}

public static class PasswordRules
{
    public const int MinLength = 8;
    public const int MaxLength = 128;

    /// <summary>Same rule as the app: at least 8 characters with a letter and a digit.</summary>
    public static bool IsValid(string? password) =>
        password is { Length: >= MinLength and <= MaxLength } && password.Any(char.IsLetter) && password.Any(char.IsDigit);
}

public static class Roles
{
    public const string Owner = "owner";
    public const string Distributor = "distributor";
    public const string Technician = "ggp_technician";

    public static bool IsValid(string role) => role is Owner or Distributor or Technician;
}
