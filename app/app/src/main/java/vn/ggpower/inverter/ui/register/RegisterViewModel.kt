package vn.ggpower.inverter.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import vn.ggpower.inverter.data.auth.RegisterResult
import vn.ggpower.inverter.domain.auth.AccountInput
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.domain.auth.RegisterFieldError
import vn.ggpower.inverter.domain.auth.RegistrationInput
import vn.ggpower.inverter.domain.auth.checkRegistration
import vn.ggpower.inverter.domain.auth.maskedDestination
import vn.ggpower.inverter.ui.otp.OtpStep
import vn.ggpower.inverter.ui.otp.OtpUiState
import javax.inject.Inject

/** Lỗi trong hộp đỏ của 13a. */
enum class RegisterFormError { NoConnection, Server }

/**
 * Bước 13a · Thông tin. Lỗi dưới ô chỉ hiện sau khi bấm Tạo tài khoản; sửa ô thì lỗi ô đó ẩn. Nút chỉ bật khi đã đánh
 * dấu đồng ý điều khoản (Register.dc.html).
 */
data class RegisterInfoUiState(
    val input: RegistrationInput = RegistrationInput(),
    val agreed: Boolean = false,
    val nameError: RegisterFieldError? = null,
    val emailError: RegisterFieldError? = null,
    val phoneError: RegisterFieldError? = null,
    val passwordError: PasswordError? = null,
    val confirmError: PasswordError? = null,
    val formError: RegisterFormError? = null,
    val submitting: Boolean = false,
) {
    val canSubmit: Boolean get() = agreed
}

/** ViewModel của cả luồng Đăng ký (13a → 13b), gắn với nested graph `RegisterFlow`; quay lại 13a không mất dữ liệu. */
@HiltViewModel
class RegisterViewModel @Inject constructor(auth: AuthRepository) : ViewModel() {
    private val controller = RegisterController(auth, viewModelScope)
    val info: StateFlow<RegisterInfoUiState> get() = controller.info
    val codeSent: Flow<Unit> get() = controller.codeSent
    fun onNameChange(value: String) = controller.onNameChange(value)
    fun onEmailChange(value: String) = controller.onEmailChange(value)
    fun onPhoneChange(value: String) = controller.onPhoneChange(value)
    fun onPasswordChange(value: String) = controller.onPasswordChange(value)
    fun onConfirmChange(value: String) = controller.onConfirmChange(value)
    fun onAgreeToggle() = controller.onAgreeToggle()
    fun onSubmit() = controller.onSubmit()
    val otp: StateFlow<OtpUiState> get() = controller.otp
    val registered: Flow<Unit> get() = controller.registered
    fun onCodeChange(value: String) = controller.onCodeChange(value)
    fun onVerify() = controller.onVerify()
    fun onResend() = controller.onResend()
}

/** Tách khỏi ViewModel để test. */
class RegisterController(
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _info = MutableStateFlow(RegisterInfoUiState())
    val info: StateFlow<RegisterInfoUiState> = _info.asStateFlow()

    /** Số điện thoại đã nhận mã; 13b xác thực số này. */
    var phone: AccountInput.Phone? = null
        private set

    private val _codeSent = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi backend đã gửi mã: 13a chuyển sang 13b. */
    val codeSent: Flow<Unit> = _codeSent.receiveAsFlow()

    private val _registered = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi xác thực xong (đã đăng nhập): 13b chuyển sang Dự án trạng thái rỗng. */
    val registered: Flow<Unit> = _registered.receiveAsFlow()

    private val otpStep = OtpStep(
        scope,
        resend = { auth.resendRegistrationCode(phone!!) },
        verify = { code -> auth.verifyRegistration(phone!!, code) },
        onVerified = { _registered.send(Unit) },
    )
    val otp: StateFlow<OtpUiState> get() = otpStep.state

    fun onCodeChange(value: String) = otpStep.onCodeChange(value)
    fun onVerify() { if (phone != null) otpStep.onVerify() }
    fun onResend() { if (phone != null) otpStep.onResend() }

    private fun edit(block: (RegisterInfoUiState) -> RegisterInfoUiState) = _info.update { block(it).copy(formError = null) }

    fun onNameChange(value: String) = edit { it.copy(input = it.input.copy(name = value), nameError = null) }
    fun onEmailChange(value: String) = edit { it.copy(input = it.input.copy(email = value), emailError = null) }
    fun onPhoneChange(value: String) = edit { it.copy(input = it.input.copy(phone = value), phoneError = null) }
    fun onPasswordChange(value: String) = edit { it.copy(input = it.input.copy(password = value), passwordError = null) }
    fun onConfirmChange(value: String) = edit { it.copy(input = it.input.copy(confirm = value), confirmError = null) }
    fun onAgreeToggle() = edit { it.copy(agreed = !it.agreed) }

    fun onSubmit() {
        val current = _info.value
        if (!current.canSubmit || current.submitting) return
        val check = checkRegistration(current.input)
        _info.update {
            it.copy(
                nameError = check.nameError, emailError = check.emailError, phoneError = check.phoneError,
                passwordError = check.passwordError, confirmError = check.confirmError, formError = null,
            )
        }
        val email = check.email
        val phone = check.phone
        if (!check.valid || email == null || phone == null) return
        _info.update { it.copy(submitting = true) }
        scope.launch {
            val result = auth.register(current.input.name.trim(), email, phone, current.input.password)
            _info.update {
                when (result) {
                    RegisterResult.CodeSent -> it.copy(submitting = false)
                    RegisterResult.EmailTaken -> it.copy(submitting = false, emailError = RegisterFieldError.EmailTaken)
                    RegisterResult.PhoneTaken -> it.copy(submitting = false, phoneError = RegisterFieldError.PhoneTaken)
                    RegisterResult.NoConnection -> it.copy(submitting = false, formError = RegisterFormError.NoConnection)
                    RegisterResult.ServerError -> it.copy(submitting = false, formError = RegisterFormError.Server)
                }
            }
            if (result == RegisterResult.CodeSent) {
                this@RegisterController.phone = phone
                otpStep.start(maskedDestination(phone))
                _codeSent.send(Unit)
            }
        }
    }
}
