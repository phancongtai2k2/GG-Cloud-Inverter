package vn.ggpower.inverter.ui.forgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.OtpForm
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.components.boldArgument
import vn.ggpower.inverter.ui.otp.OtpError
import vn.ggpower.inverter.ui.otp.OtpUiState
import vn.ggpower.inverter.ui.otp.otpErrorText
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun ForgotOtpRoute(viewModel: ForgotPasswordViewModel, onBack: () -> Unit, onVerified: () -> Unit) {
    val state by viewModel.otp.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.codeVerified.collect { onVerified() } }
    ForgotOtpScreen(state, onBack, viewModel::onCodeChange, viewModel::onVerify, viewModel::onResend)
}

/**
 * Màn 12b · Quên mật khẩu – Mã xác thực (ForgotPasswordOtp.png; đặc tả §3.12 bước 2). Đếm ngược 60 giây trước khi
 * được gửi lại mã; mã sống 5 phút; sai 5 lần liên tiếp khoá 15 phút. Quay lại về 12a, giữ tài khoản đã nhập.
 */
@Composable
fun ForgotOtpScreen(
    state: OtpUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.forgot_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        ) {
            StepProgress(forgotSteps(), current = 2)
            OtpForm(
                heading = stringResource(R.string.forgot_otp_heading),
                body = boldArgument(stringResource(R.string.forgot_otp_body), state.destination, colors.ink),
                code = state.code,
                onCodeChange = onCodeChange,
                errorText = otpErrorText(state.error),
                resendIn = state.resendIn,
                resending = state.resending,
                onResend = onResend,
                confirmLabel = stringResource(R.string.forgot_otp_confirm),
                verifying = state.verifying,
                onConfirm = { keyboard?.hide(); focus.clearFocus(); onVerify() },
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotOtpPreview() {
    GgPowerTheme(darkTheme = false) {
        ForgotOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "482913", resendIn = 45), {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotOtpWrongPreview() {
    GgPowerTheme(darkTheme = false) {
        ForgotOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "482910", error = OtpError.Wrong(4)), {}, {}, {}, {})
    }
}
