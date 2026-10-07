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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.login.AccountError
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun ForgotFindRoute(viewModel: ForgotPasswordViewModel, onBack: () -> Unit, onCodeSent: () -> Unit) {
    val state by viewModel.find.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.codeSent.collect { onCodeSent() } }
    ForgotFindScreen(state, onBack, viewModel::onAccountChange, viewModel::onSendCode)
}

/** Ba bước của luồng Quên mật khẩu (12a–12c); 12d tô đủ ba vạch. */
@Composable
fun forgotSteps() = listOf(
    stringResource(R.string.forgot_step_account),
    stringResource(R.string.forgot_step_code),
    stringResource(R.string.forgot_step_password),
)

/**
 * Màn 12a · Quên mật khẩu – Tìm tài khoản (ForgotPassword.png; đặc tả §3.12 bước 1). Ô tài khoản điền sẵn chữ đã gõ ở
 * Đăng nhập. Dù tài khoản có tồn tại hay không, gửi xong đều sang 12b (không tiết lộ tài khoản).
 */
@Composable
fun ForgotFindScreen(
    state: ForgotFindUiState,
    onBack: () -> Unit,
    onAccountChange: (String) -> Unit,
    onSendCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val send = { keyboard?.hide(); focus.clearFocus(); onSendCode() }
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.forgot_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        ) {
            StepProgress(forgotSteps(), current = 1)
            Text(
                stringResource(R.string.forgot_find_heading),
                style = GgTheme.typography.body.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.forgot_find_body),
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            GgTextField(
                label = stringResource(R.string.login_account_label),
                value = state.account,
                onValueChange = onAccountChange,
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                errorText = when (state.accountError) {
                    AccountError.Empty -> stringResource(R.string.login_error_account_empty)
                    AccountError.Invalid -> stringResource(R.string.login_error_account_invalid)
                    null -> null
                },
                modifier = Modifier.semantics { contentType = ContentType.Username },
            )
            state.formError?.let { error ->
                ErrorBox(
                    stringResource(
                        when (error) {
                            FindError.TooManyRequests -> R.string.forgot_error_too_many
                            FindError.NoConnection -> R.string.error_no_internet
                            FindError.Server -> R.string.error_server
                        },
                    ),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.forgot_send_code),
                onClick = send,
                loading = state.sending,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.forgot_find_note),
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.inkMuted,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotFindPreview() {
    GgPowerTheme(darkTheme = false) { ForgotFindScreen(ForgotFindUiState(account = "0912 345 678"), {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotFindErrorPreview() {
    GgPowerTheme(darkTheme = false) {
        ForgotFindScreen(ForgotFindUiState(account = "0912", accountError = AccountError.Invalid), {}, {}, {})
    }
}
