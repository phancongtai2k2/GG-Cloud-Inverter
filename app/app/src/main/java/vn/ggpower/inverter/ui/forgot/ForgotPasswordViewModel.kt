package vn.ggpower.inverter.ui.forgot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.ResetPasswordResult
import vn.ggpower.inverter.domain.auth.AccountInput
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.domain.auth.confirmPasswordError
import vn.ggpower.inverter.domain.auth.newPasswordError
import vn.ggpower.inverter.domain.auth.maskedDestination
import vn.ggpower.inverter.ui.otp.OtpStep
import vn.ggpower.inverter.ui.otp.OtpUiState
import vn.ggpower.inverter.navigation.ForgotPasswordFlow
import vn.ggpower.inverter.ui.login.AccountError
import javax.inject.Inject

/** Lỗi trong hộp đỏ của 12a. */
enum class FindError { TooManyRequests, NoConnection, Server }

/** Bước 12a · Tìm tài khoản. */
data class ForgotFindUiState(
    val account: String = "",
    val accountError: AccountError? = null,
    val formError: FindError? = null,
    val sending: Boolean = false,
)

/** Lỗi trong hộp đỏ của 12c (BO_SUNG §19). */
enum class NewPasswordError { Expired, NoConnection, Server }

/** Bước 12c · Mật khẩu mới. Lỗi dưới ô chỉ hiện sau khi bấm Đổi mật khẩu; sửa ô thì lỗi ô đó ẩn. */
data class ForgotNewUiState(
    val password: String = "",
    val confirm: String = "",
    val passwordError: PasswordError? = null,
    val confirmError: PasswordError? = null,
    val formError: NewPasswordError? = null,
    val saving: Boolean = false,
)

/**
 * ViewModel của cả luồng Quên mật khẩu (12a → 12d), gắn với nested graph [ForgotPasswordFlow] để dữ liệu không mất khi
 * quay lại bước trước (screens.md "Luồng nhiều bước").
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: AuthRepository,
) : ViewModel() {
    private val controller = ForgotPasswordController(savedStateHandle.toRoute<ForgotPasswordFlow>().account, auth, viewModelScope)
    val find: StateFlow<ForgotFindUiState> get() = controller.find
    val codeSent: Flow<Unit> get() = controller.codeSent
    fun onAccountChange(value: String) = controller.onAccountChange(value)
    fun onSendCode() = controller.onSendCode()
    val otp: StateFlow<OtpUiState> get() = controller.otp
    val codeVerified: Flow<Unit> get() = controller.codeVerified
    fun onCodeChange(value: String) = controller.onCodeChange(value)
    fun onVerify() = controller.onVerify()
    fun onResend() = controller.onResend()
    val newPassword: StateFlow<ForgotNewUiState> get() = controller.newPassword
    val passwordChanged: Flow<Unit> get() = controller.passwordChanged
    fun onPasswordChange(value: String) = controller.onPasswordChange(value)
    fun onConfirmChange(value: String) = controller.onConfirmChange(value)
    fun onChangePassword() = controller.onChangePassword()
    /** Tài khoản điền sẵn vào Đăng nhập khi luồng kết thúc (đặc tả §3.12). */
    val accountForLogin: String get() = controller.find.value.account.trim()
}

/** Tách khỏi ViewModel để test không cần SavedStateHandle. */
class ForgotPasswordController(
    initialAccount: String,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _find = MutableStateFlow(ForgotFindUiState(account = initialAccount))
    val find: StateFlow<ForgotFindUiState> = _find.asStateFlow()

    /** Tài khoản đã gửi mã thành công; 12b hiện nơi nhận mã từ đây. */
    var destination: AccountInput? = null
        private set

    private val _codeSent = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi đã gửi mã: màn 12a chuyển sang 12b. */
    val codeSent: Flow<Unit> = _codeSent.receiveAsFlow()

    /** Mã xác nhận trả về khi đúng mã; 12c dùng để đặt mật khẩu mới. */
    var resetToken: String? = null
        private set

    private val _codeVerified = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi mã đúng: 12b chuyển sang 12c. */
    val codeVerified: Flow<Unit> = _codeVerified.receiveAsFlow()

    private val otpStep = OtpStep(
        scope,
        resend = { auth.requestResetCode(destination!!) },
        verify = { code -> auth.verifyResetCode(destination!!, code) },
        onVerified = { token ->
            resetToken = token
            _codeVerified.send(Unit)
        },
    )
    val otp: StateFlow<OtpUiState> get() = otpStep.state

    private val _new = MutableStateFlow(ForgotNewUiState())
    val newPassword: StateFlow<ForgotNewUiState> = _new.asStateFlow()

    private val _passwordChanged = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi đổi mật khẩu thành công: 12c chuyển sang 12d. */
    val passwordChanged: Flow<Unit> = _passwordChanged.receiveAsFlow()

    fun onAccountChange(value: String) = _find.update { it.copy(account = value, accountError = null, formError = null) }

    fun onSendCode() {
        val current = _find.value
        if (current.sending) return
        val account = AccountInput.parse(current.account)
        val error = when (account) {
            AccountInput.Empty -> AccountError.Empty
            AccountInput.Invalid -> AccountError.Invalid
            else -> null
        }
        if (error != null) {
            _find.update { it.copy(accountError = error, formError = null) }
            return
        }
        _find.update { it.copy(sending = true, formError = null) }
        scope.launch {
            val result = auth.requestResetCode(account)
            val formError = when (result) {
                CodeSendResult.Sent -> null
                CodeSendResult.TooManyRequests -> FindError.TooManyRequests
                CodeSendResult.NoConnection -> FindError.NoConnection
                CodeSendResult.ServerError -> FindError.Server
            }
            _find.update { it.copy(sending = false, formError = formError) }
            if (result == CodeSendResult.Sent) {
                destination = account
                otpStep.start(maskedDestination(account))
                _codeSent.send(Unit)
            }
        }
    }

    fun onCodeChange(value: String) = otpStep.onCodeChange(value)

    fun onVerify() { if (destination != null) otpStep.onVerify() }

    /** "Gửi lại mã": chỉ khi hết đếm ngược; gửi được thì xoá mã đang nhập và đếm lại 60 giây. */
    fun onResend() { if (destination != null) otpStep.onResend() }

    fun onPasswordChange(value: String) = _new.update { it.copy(password = value, passwordError = null, formError = null) }

    fun onConfirmChange(value: String) = _new.update { it.copy(confirm = value, confirmError = null, formError = null) }

    fun onChangePassword() {
        val current = _new.value
        val token = resetToken ?: return
        if (current.saving) return
        val passwordError = newPasswordError(current.password)
        val confirmError = confirmPasswordError(current.password, current.confirm)
        if (passwordError != null || confirmError != null) {
            _new.update { it.copy(passwordError = passwordError, confirmError = confirmError, formError = null) }
            return
        }
        _new.update { it.copy(saving = true, formError = null) }
        scope.launch {
            val result = auth.resetPassword(token, current.password)
            val formError = when (result) {
                ResetPasswordResult.Success -> null
                ResetPasswordResult.Expired -> NewPasswordError.Expired
                ResetPasswordResult.NoConnection -> NewPasswordError.NoConnection
                ResetPasswordResult.ServerError -> NewPasswordError.Server
            }
            _new.update { it.copy(saving = false, formError = formError) }
            if (result == ResetPasswordResult.Success) _passwordChanged.send(Unit)
        }
    }

}
