package vn.ggpower.inverter.ui.otp

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.VerifyCodeResult
import vn.ggpower.inverter.ui.components.OTP_LENGTH

/** Lỗi ở bước nhập mã 12b / 13b (BO_SUNG §18). */
sealed interface OtpError {
    data class Wrong(val attemptsLeft: Int) : OtpError
    data object Expired : OtpError
    data object Locked : OtpError
    /** Gửi lại mã quá 3 lần / 10 phút. */
    data object TooManyRequests : OtpError
    data object NoConnection : OtpError
    data object Server : OtpError
}

/** Trạng thái bước nhập mã. [resendIn] là số giây còn lại trước khi được gửi lại mã (0: được gửi). */
data class OtpUiState(
    val destination: String = "",
    val code: String = "",
    val error: OtpError? = null,
    val verifying: Boolean = false,
    val resendIn: Int = 0,
    val resending: Boolean = false,
)

/** Câu lỗi của bước nhập mã (BO_SUNG §18). */
@Composable
fun otpErrorText(error: OtpError?): String? = when (error) {
    null -> null
    is OtpError.Wrong -> pluralStringResource(R.plurals.otp_error_wrong, error.attemptsLeft, error.attemptsLeft)
    OtpError.Expired -> stringResource(R.string.otp_error_expired)
    OtpError.Locked -> stringResource(R.string.otp_error_locked)
    OtpError.TooManyRequests -> stringResource(R.string.forgot_error_too_many)
    OtpError.NoConnection -> stringResource(R.string.error_no_internet)
    OtpError.Server -> stringResource(R.string.error_server)
}

/**
 * Logic dùng chung của bước nhập mã (12b, 13b): đếm ngược [RESEND_SECONDS] giây trước khi được gửi lại; "Gửi lại mã"
 * xoá mã đang nhập và đếm lại; chỉ kiểm tra khi đủ 6 số; mã đúng thì gọi [onVerified] với mã xác nhận của backend.
 */
class OtpStep(
    private val scope: CoroutineScope,
    private val resend: suspend () -> CodeSendResult,
    private val verify: suspend (code: String) -> VerifyCodeResult,
    private val onVerified: suspend (token: String) -> Unit,
) {
    private val _state = MutableStateFlow(OtpUiState())
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    private var countdown: Job? = null

    /** Vào bước nhập mã sau khi đã gửi mã tới [destination] (đã che bớt). */
    fun start(destination: String) {
        _state.value = OtpUiState(destination = destination)
        startCountdown()
    }

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, error = null) }

    fun onVerify() {
        val current = _state.value
        if (current.verifying || current.code.length < OTP_LENGTH) return
        _state.update { it.copy(verifying = true, error = null) }
        scope.launch {
            val result = verify(current.code)
            val error = when (result) {
                is VerifyCodeResult.Valid -> null
                is VerifyCodeResult.Wrong -> OtpError.Wrong(result.attemptsLeft)
                VerifyCodeResult.Expired -> OtpError.Expired
                VerifyCodeResult.Locked -> OtpError.Locked
                VerifyCodeResult.NoConnection -> OtpError.NoConnection
                VerifyCodeResult.ServerError -> OtpError.Server
            }
            _state.update { it.copy(verifying = false, error = error) }
            if (result is VerifyCodeResult.Valid) onVerified(result.token)
        }
    }

    fun onResend() {
        val current = _state.value
        if (current.resendIn > 0 || current.resending) return
        _state.update { it.copy(resending = true, error = null) }
        scope.launch {
            val error = when (resend()) {
                CodeSendResult.Sent -> null
                CodeSendResult.TooManyRequests -> OtpError.TooManyRequests
                CodeSendResult.NoConnection -> OtpError.NoConnection
                CodeSendResult.ServerError -> OtpError.Server
            }
            _state.update { if (error == null) it.copy(code = "", resending = false) else it.copy(resending = false, error = error) }
            if (error == null) startCountdown()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = scope.launch {
            for (s in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendIn = s) }
                if (s > 0) delay(1_000)
            }
        }
    }

    companion object {
        /** Đếm ngược trước khi được gửi lại mã (đặc tả §3.12). */
        const val RESEND_SECONDS = 60
    }
}
