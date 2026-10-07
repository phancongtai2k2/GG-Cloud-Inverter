package vn.ggpower.inverter.domain.auth

/** Lỗi dưới ô của 13a (BO_SUNG §20). [EmailTaken] / [PhoneTaken] do backend báo (đặc tả §3.13). */
enum class RegisterFieldError { NameEmpty, EmailEmpty, EmailInvalid, EmailTaken, PhoneEmpty, PhoneInvalid, PhoneTaken }

/** Dữ liệu nhập ở 13a. */
data class RegistrationInput(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val confirm: String = "",
)

/** Kết quả kiểm tra 13a; [valid] khi không ô nào lỗi. [email] / [phone] là giá trị đã chuẩn hoá để gửi backend. */
data class RegistrationCheck(
    val nameError: RegisterFieldError? = null,
    val emailError: RegisterFieldError? = null,
    val phoneError: RegisterFieldError? = null,
    val passwordError: PasswordError? = null,
    val confirmError: PasswordError? = null,
    val email: AccountInput.Email? = null,
    val phone: AccountInput.Phone? = null,
) {
    val valid: Boolean
        get() = nameError == null && emailError == null && phoneError == null && passwordError == null && confirmError == null
}

/**
 * Kiểm tra 13a (đặc tả §3.13): họ tên không trống; ô Email phải là email; ô Số điện thoại phải là số di động Việt Nam;
 * mật khẩu theo [isValidPassword]; nhập lại trùng khớp.
 */
fun checkRegistration(input: RegistrationInput): RegistrationCheck {
    val email = if (input.email.isBlank()) null else AccountInput.parse(input.email) as? AccountInput.Email
    val phone = if (input.phone.isBlank() || '@' in input.phone) null else AccountInput.parse(input.phone) as? AccountInput.Phone
    return RegistrationCheck(
        nameError = if (input.name.isBlank()) RegisterFieldError.NameEmpty else null,
        emailError = when {
            input.email.isBlank() -> RegisterFieldError.EmailEmpty
            email == null -> RegisterFieldError.EmailInvalid
            else -> null
        },
        phoneError = when {
            input.phone.isBlank() -> RegisterFieldError.PhoneEmpty
            phone == null -> RegisterFieldError.PhoneInvalid
            else -> null
        },
        passwordError = newPasswordError(input.password),
        confirmError = confirmPasswordError(input.password, input.confirm),
        email = email,
        phone = phone,
    )
}
