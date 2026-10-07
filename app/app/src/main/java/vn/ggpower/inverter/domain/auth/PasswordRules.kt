package vn.ggpower.inverter.domain.auth

/** Độ dài tối thiểu của mật khẩu (đặc tả §3.12, §3.13). */
const val PASSWORD_MIN_LENGTH = 8

/** Lỗi của cặp ô "Mật khẩu mới" / "Nhập lại mật khẩu" (12c, 13a). */
enum class PasswordError { Empty, TooWeak, Mismatch, ConfirmEmpty }

/** Mật khẩu hợp lệ: tối thiểu 8 ký tự, có cả chữ và số (đặc tả §3.12 "Quy tắc"). */
fun isValidPassword(password: String): Boolean =
    password.length >= PASSWORD_MIN_LENGTH && password.any { it.isLetter() } && password.any { it.isDigit() }

/** Lỗi của ô mật khẩu mới; null nếu hợp lệ. */
fun newPasswordError(password: String): PasswordError? = when {
    password.isEmpty() -> PasswordError.Empty
    !isValidPassword(password) -> PasswordError.TooWeak
    else -> null
}

/** Lỗi của ô nhập lại; null nếu trùng khớp. */
fun confirmPasswordError(password: String, confirm: String): PasswordError? = when {
    confirm.isEmpty() -> PasswordError.ConfirmEmpty
    confirm != password -> PasswordError.Mismatch
    else -> null
}

/**
 * Mức độ mạnh trên thanh 4 vạch (ForgotPasswordNew.dc.html "Độ mạnh: Tốt", BO_SUNG §19). [bars] là số vạch tô.
 * - [Weak]: chưa đạt quy tắc (dưới 8 ký tự hoặc thiếu chữ / số).
 * - [Fair]: đạt quy tắc.
 * - [Good]: đạt quy tắc và có thêm 1 trong: chữ hoa lẫn chữ thường, ký tự đặc biệt, từ 12 ký tự.
 * - [Strong]: có thêm từ 2 điều trên.
 */
enum class PasswordStrength(val bars: Int) { Weak(1), Fair(2), Good(3), Strong(4) }

/** Độ mạnh của [password]; null khi ô còn trống (thanh để xám, không có chữ). */
fun passwordStrength(password: String): PasswordStrength? {
    if (password.isEmpty()) return null
    if (!isValidPassword(password)) return PasswordStrength.Weak
    val extras = listOf(
        password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
        password.any { !it.isLetterOrDigit() },
        password.length >= 12,
    ).count { it }
    return when (extras) {
        0 -> PasswordStrength.Fair
        1 -> PasswordStrength.Good
        else -> PasswordStrength.Strong
    }
}
