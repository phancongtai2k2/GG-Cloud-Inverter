package vn.ggpower.inverter.ui.forgot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.domain.auth.passwordStrength
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.PasswordField
import vn.ggpower.inverter.ui.components.PasswordStrengthMeter
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun ForgotNewRoute(viewModel: ForgotPasswordViewModel, onBack: () -> Unit, onChanged: () -> Unit) {
    val state by viewModel.newPassword.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.passwordChanged.collect { onChanged() } }
    ForgotNewScreen(state, onBack, viewModel::onPasswordChange, viewModel::onConfirmChange, viewModel::onChangePassword)
}

/** Câu lỗi dưới ô mật khẩu (BO_SUNG §19); dùng chung cho 13a. */
@Composable
fun passwordErrorText(error: PasswordError?): String? = when (error) {
    null -> null
    PasswordError.Empty -> stringResource(R.string.password_error_empty)
    PasswordError.TooWeak -> stringResource(R.string.password_error_weak)
    PasswordError.ConfirmEmpty -> stringResource(R.string.password_error_confirm_empty)
    PasswordError.Mismatch -> stringResource(R.string.password_error_mismatch)
}

/**
 * Màn 12c · Quên mật khẩu – Mật khẩu mới (ForgotPasswordNew.png; đặc tả §3.12 bước 3). Thanh độ mạnh cập nhật khi gõ;
 * kiểm tra quy tắc (≥ 8 ký tự, có chữ và số) và trùng khớp khi bấm Đổi mật khẩu.
 */
@Composable
fun ForgotNewScreen(
    state: ForgotNewUiState,
    onBack: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); focus.clearFocus(); onSubmit() }
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.forgot_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        ) {
            StepProgress(forgotSteps(), current = 3)
            Text(
                stringResource(R.string.forgot_new_heading),
                style = GgTheme.typography.body.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            PasswordField(
                label = stringResource(R.string.forgot_new_password),
                value = state.password,
                onValueChange = onPasswordChange,
                errorText = passwordErrorText(state.passwordError),
                onImeAction = { focus.moveFocus(FocusDirection.Down) },
            )
            PasswordStrengthMeter(passwordStrength(state.password))
            PasswordField(
                label = stringResource(R.string.forgot_new_confirm),
                value = state.confirm,
                onValueChange = onConfirmChange,
                errorText = passwordErrorText(state.confirmError),
                imeAction = ImeAction.Done,
                onImeAction = submit,
            )
            Text(stringResource(R.string.forgot_new_note), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            state.formError?.let { error ->
                ErrorBox(
                    stringResource(
                        when (error) {
                            NewPasswordError.Expired -> R.string.forgot_error_session_expired
                            NewPasswordError.NoConnection -> R.string.error_no_internet
                            NewPasswordError.Server -> R.string.error_server
                        },
                    ),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.forgot_new_submit),
                onClick = submit,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotNewPreview() {
    GgPowerTheme(darkTheme = false) {
        ForgotNewScreen(ForgotNewUiState(password = "GGpower2026", confirm = "GGpower2026"), {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotNewErrorPreview() {
    GgPowerTheme(darkTheme = false) {
        ForgotNewScreen(
            ForgotNewUiState(password = "abc12", confirm = "abc", passwordError = PasswordError.TooWeak, confirmError = PasswordError.Mismatch),
            {}, {}, {}, {},
        )
    }
}
