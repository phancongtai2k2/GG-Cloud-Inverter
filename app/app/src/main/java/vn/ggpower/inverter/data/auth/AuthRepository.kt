package vn.ggpower.inverter.data.auth

import kotlinx.coroutines.flow.Flow
import vn.ggpower.inverter.domain.auth.AccountInput

/** Kết quả đăng nhập. Chưa có hợp đồng API (docs/api/README.md) nên chỉ phân loại, không gắn mã lỗi backend. */
sealed interface LoginResult {
    data object Success : LoginResult
    data object InvalidCredentials : LoginResult
    data object NoConnection : LoginResult
    data object ServerError : LoginResult
}

/**
 * Kết quả yêu cầu gửi mã đặt lại mật khẩu (12a). Backend không cho biết tài khoản có tồn tại hay không (đặc tả §3.12):
 * tài khoản chưa đăng ký vẫn là [Sent].
 */
sealed interface CodeSendResult {
    data object Sent : CodeSendResult
    /** Quá 3 lần gửi mã trong 10 phút cho cùng tài khoản (đặc tả §3.12). */
    data object TooManyRequests : CodeSendResult
    data object NoConnection : CodeSendResult
    data object ServerError : CodeSendResult
}

/** Kết quả kiểm tra mã 6 số (12b, 13b). Quy tắc theo đặc tả §3.12: mã sống 5 phút; sai 5 lần liên tiếp khoá 15 phút. */
sealed interface VerifyCodeResult {
    /** Mã đúng; [token] dùng cho bước sau (12c đặt mật khẩu mới). */
    data class Valid(val token: String) : VerifyCodeResult
    data class Wrong(val attemptsLeft: Int) : VerifyCodeResult
    data object Expired : VerifyCodeResult
    data object Locked : VerifyCodeResult
    data object NoConnection : VerifyCodeResult
    data object ServerError : VerifyCodeResult
}

/** Kết quả đặt mật khẩu mới (12c). Thành công thì backend đăng xuất mọi phiên khác (đặc tả §3.12). */
sealed interface ResetPasswordResult {
    data object Success : ResetPasswordResult
    /** Mã xác nhận của bước 12b không còn hiệu lực. */
    data object Expired : ResetPasswordResult
    data object NoConnection : ResetPasswordResult
    data object ServerError : ResetPasswordResult
}

/** Kết quả gửi thông tin đăng ký (13a). Gửi được thì backend gửi mã OTP tới số điện thoại (13b). */
sealed interface RegisterResult {
    data object CodeSent : RegisterResult
    /** Email / số điện thoại là duy nhất trên toàn hệ thống (đặc tả §3.13). */
    data object EmailTaken : RegisterResult
    data object PhoneTaken : RegisterResult
    data object NoConnection : RegisterResult
    data object ServerError : RegisterResult
}

interface AuthRepository {
    /** Có phiên đăng nhập đã lưu hay không (§3.1: lần mở sau đi thẳng vào Dự án). */
    val isLoggedIn: Flow<Boolean>

    suspend fun login(account: AccountInput, password: String): LoginResult

    suspend fun logout()

    /** Gửi mã 6 số tới email / số điện thoại để đặt lại mật khẩu. */
    suspend fun requestResetCode(account: AccountInput): CodeSendResult

    /** Kiểm tra mã đặt lại mật khẩu đã gửi tới [account]. */
    suspend fun verifyResetCode(account: AccountInput, code: String): VerifyCodeResult

    /** Đặt mật khẩu mới bằng [token] nhận được khi mã đúng. */
    suspend fun resetPassword(token: String, newPassword: String): ResetPasswordResult

    /** Tạo tài khoản chủ hộ (đặc tả §3.13); tài khoản chỉ dùng được sau khi xác thực số điện thoại ở 13b. */
    suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult

    /** Gửi lại mã xác thực số điện thoại (13b). */
    suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult

    /** Kiểm tra mã 13b; đúng thì tài khoản được kích hoạt và đăng nhập luôn. */
    suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult
}
