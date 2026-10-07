package vn.ggpower.inverter.ui.register

import androidx.compose.foundation.background
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
import vn.ggpower.inverter.ui.components.boldArgument
import vn.ggpower.inverter.ui.otp.OtpUiState
import vn.ggpower.inverter.ui.otp.otpErrorText
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun RegisterOtpRoute(viewModel: RegisterViewModel, onBack: () -> Unit, onRegistered: () -> Unit) {
    val state by viewModel.otp.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.registered.collect { onRegistered() } }
    RegisterOtpScreen(state, onBack, viewModel::onCodeChange, viewModel::onVerify, viewModel::onResend)
}

/**
 * Màn 13b · Đăng ký – Xác thực OTP (RegisterOtp.png; đặc tả §3.13). Cùng thân với 12b ([OtpForm]), không có thanh
 * bước. Xác thực xong vào Dự án ở trạng thái trống; quay lại về 13a, giữ dữ liệu đã nhập.
 */
@Composable
fun RegisterOtpScreen(
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
        ScreenHeader(title = stringResource(R.string.register_title), onBack = onBack)
        OtpForm(
            heading = stringResource(R.string.register_otp_heading),
            body = boldArgument(stringResource(R.string.register_otp_body), state.destination, colors.ink),
            code = state.code,
            onCodeChange = onCodeChange,
            errorText = otpErrorText(state.error),
            resendIn = state.resendIn,
            resending = state.resending,
            onResend = onResend,
            confirmLabel = stringResource(R.string.register_otp_confirm),
            verifying = state.verifying,
            onConfirm = { keyboard?.hide(); focus.clearFocus(); onVerify() },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RegisterOtpPreview() {
    GgPowerTheme(darkTheme = false) {
        RegisterOtpScreen(OtpUiState(destination = "0912 ••• 678", code = "730516", resendIn = 52), {}, {}, {}, {})
    }
}
