package vn.ggpower.inverter.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgSnackbar
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.LinkText
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 1 · Đăng nhập: nối ViewModel với điều hướng. */
@Composable
fun LoginRoute(
    onLoggedIn: () -> Unit,
    /** Mở 12a, mang theo chữ đang có trong ô tài khoản. */
    onForgotPassword: (String) -> Unit,
    onRegister: () -> Unit,
    /** Tài khoản điền sẵn khi quay về từ 12d (đặc tả §3.12); gọi [onPrefillConsumed] sau khi điền. */
    prefillAccount: String? = null,
    onPrefillConsumed: () -> Unit = {},
    /** Thông báo hiện 4 giây ở đáy màn (ví dụ sau khi xoá tài khoản); hết giờ gọi [onNoticeShown]. */
    notice: String? = null,
    onNoticeShown: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noticeShown by rememberUpdatedState(onNoticeShown)
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4_000)
            noticeShown()
        }
    }
    LaunchedEffect(viewModel) { viewModel.loggedIn.collect { onLoggedIn() } }
    LaunchedEffect(prefillAccount) {
        if (prefillAccount != null) {
            viewModel.onAccountChange(prefillAccount)
            viewModel.onPasswordChange("")
            onPrefillConsumed()
        }
    }
    LoginScreen(
        state = state,
        onAccountChange = viewModel::onAccountChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisible = viewModel::onTogglePasswordVisible,
        onSubmit = viewModel::onSubmit,
        onForgotPassword = { onForgotPassword(state.account) },
        onRegister = onRegister,
        notice = notice,
    )
}

/**
 * Màn 1 · Đăng nhập (docs/design/screens/Login.png, html/Login.dc.html, đặc tả §3.1). Không có nút "Thêm thiết bị bằng
 * mã QR" (ADR-004): quét QR chỉ mở từ bước 1 của Thêm dự án.
 * Lề 48 / 20 / 32 dp; khối đầu, biểu mẫu và chân trang cách nhau 32 dp; chân trang dồn xuống đáy.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onRegister: () -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val colors = GgTheme.colors
    val type = GgTheme.typography
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Đóng bàn phím trước khi gửi để thấy hộp lỗi / vòng xoay.
    val submit = { keyboard?.hide(); focus.clearFocus(); onSubmit() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
            .imePadding(),
    ) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = 48.dp, bottom = GgSpacing.space8),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space8)) {
                // Đầu trang: logo, tiêu đề, mô tả
                Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space6)) {
                    Image(
                        painter = painterResource(R.drawable.logo_gg_power),
                        contentDescription = stringResource(R.string.cd_logo),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(180.dp, 71.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
                        Text(
                            text = stringResource(R.string.login_title),
                            style = type.display.copy(fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.01).em),
                            color = colors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(stringResource(R.string.login_subtitle), style = type.body, color = colors.inkMuted)
                    }
                }

                // Biểu mẫu
                Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space4)) {
                    GgTextField(
                        label = stringResource(R.string.login_account_label),
                        value = state.account,
                        onValueChange = onAccountChange,
                        size = FieldSize.Large,
                        placeholder = stringResource(R.string.login_account_placeholder),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        errorText = when (state.accountError) {
                            AccountError.Empty -> stringResource(R.string.login_error_account_empty)
                            AccountError.Invalid -> stringResource(R.string.login_error_account_invalid)
                            null -> null
                        },
                        modifier = Modifier.semantics { contentType = ContentType.Username },
                    )
                    GgTextField(
                        label = stringResource(R.string.login_password_label),
                        value = state.password,
                        onValueChange = onPasswordChange,
                        size = FieldSize.Large,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        errorText = if (state.passwordEmpty) stringResource(R.string.login_error_password_empty) else null,
                        modifier = Modifier.semantics { contentType = ContentType.Password },
                        trailing = {
                            IconButton(onClick = onTogglePasswordVisible, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    painter = painterResource(if (state.passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                                    contentDescription = stringResource(
                                        if (state.passwordVisible) R.string.cd_hide_password else R.string.cd_show_password,
                                    ),
                                    tint = colors.inkMuted,
                                )
                            }
                        },
                    )
                    // "Quên mật khẩu?" canh phải, đệm dọc 16 dp như thiết kế mới: cao 52 = vùng chạm 48 + 2 × 2.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        LinkText(stringResource(R.string.login_forgot_password), onForgotPassword, Modifier.padding(vertical = 2.dp))
                    }
                    state.formError?.let { error ->
                        ErrorBox(
                            stringResource(
                                when (error) {
                                    LoginFormError.InvalidCredentials -> R.string.login_error_credentials
                                    LoginFormError.NoConnection -> R.string.error_no_internet
                                    LoginFormError.Server -> R.string.error_server
                                },
                            ),
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.login_submit),
                        onClick = submit,
                        loading = state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Chân trang
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = GgSpacing.space8),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.login_no_account) + " ",
                        style = type.bodySm,
                        color = colors.inkMuted,
                    )
                    LinkText(stringResource(R.string.login_register), onRegister)
                }
                Text(
                    text = stringResource(R.string.slogan),
                    style = type.eyebrow,
                    color = colors.primary,
                )
            }
        }
        // Thông báo một lần, ví dụ sau khi gửi yêu cầu xoá tài khoản ở màn 15 (BO_SUNG §24).
        if (notice != null) {
            GgSnackbar(
                notice,
                StatusTone.Info,
                modifier = Modifier.align(Alignment.BottomCenter).padding(GgSpacing.space5),
            )
        }
    }
}

@Composable
private fun LoginPreviewHost(state: LoginUiState) {
    GgPowerTheme(darkTheme = false) {
        LoginScreen(
            state = state,
            onAccountChange = {}, onPasswordChange = {}, onTogglePasswordVisible = {}, onSubmit = {},
            onForgotPassword = {}, onRegister = {}
        )
    }
}

@Preview(name = "Mặc định (như Login.png)", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoginPreview() = LoginPreviewHost(LoginUiState(password = "matkhau123"))

@Preview(name = "Lỗi nhập", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoginErrorPreview() = LoginPreviewHost(
    LoginUiState(account = "0912 345", accountError = AccountError.Invalid, passwordEmpty = true),
)

@Preview(name = "Sai thông tin", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoginCredentialsPreview() = LoginPreviewHost(
    LoginUiState(account = "0912 345 678", password = "sai", formError = LoginFormError.InvalidCredentials),
)

@Preview(name = "Đang xử lý", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LoginSubmittingPreview() = LoginPreviewHost(
    LoginUiState(account = "0912 345 678", password = "matkhau123", submitting = true),
)
