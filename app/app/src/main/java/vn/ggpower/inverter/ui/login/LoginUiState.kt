package vn.ggpower.inverter.ui.login

/** Lỗi dưới ô tài khoản. */
enum class AccountError { Empty, Invalid }

/** Lỗi trong hộp đỏ phía trên nút Đăng nhập. */
enum class LoginFormError { InvalidCredentials, NoConnection, Server }

data class LoginUiState(
    val account: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val accountError: AccountError? = null,
    val passwordEmpty: Boolean = false,
    val formError: LoginFormError? = null,
    val submitting: Boolean = false,
)
