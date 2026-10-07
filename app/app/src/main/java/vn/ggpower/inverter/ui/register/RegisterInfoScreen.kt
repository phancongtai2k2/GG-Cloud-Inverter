package vn.ggpower.inverter.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.domain.auth.RegisterFieldError
import vn.ggpower.inverter.domain.auth.RegistrationInput
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.LinkText
import vn.ggpower.inverter.ui.components.PasswordField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.forgot.passwordErrorText
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun RegisterInfoRoute(
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onCodeSent: () -> Unit,
    /** Về Đăng nhập; [account] là email / số đã đăng ký để điền sẵn (null: không điền). */
    onLogin: (account: String?) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val state by viewModel.info.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.codeSent.collect { onCodeSent() } }
    RegisterInfoScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPhoneChange = viewModel::onPhoneChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmChange = viewModel::onConfirmChange,
        onAgreeToggle = viewModel::onAgreeToggle,
        onSubmit = viewModel::onSubmit,
        onLogin = onLogin,
        onOpenTerms = onOpenTerms,
        onOpenPrivacy = onOpenPrivacy,
    )
}

/** Câu lỗi dưới ô của 13a (BO_SUNG §20). */
@Composable
private fun fieldErrorText(error: RegisterFieldError?): String? = error?.let {
    stringResource(
        when (it) {
            RegisterFieldError.NameEmpty -> R.string.register_error_name_empty
            RegisterFieldError.EmailEmpty -> R.string.register_error_email_empty
            RegisterFieldError.EmailInvalid -> R.string.register_error_email_invalid
            RegisterFieldError.EmailTaken -> R.string.register_error_email_taken
            RegisterFieldError.PhoneEmpty -> R.string.register_error_phone_empty
            RegisterFieldError.PhoneInvalid -> R.string.register_error_phone_invalid
            RegisterFieldError.PhoneTaken -> R.string.register_error_phone_taken
        },
    )
}

/**
 * Màn 13a · Đăng ký – Thông tin (Register.png; đặc tả §3.13). Các khối cách 14. Nút "Tạo tài khoản" chỉ bật khi đã
 * đánh dấu đồng ý điều khoản (prototype); các ô kiểm tra khi bấm nút. Email / số đã đăng ký: lỗi dưới ô kèm liên kết
 * "Đăng nhập" (điền sẵn tài khoản đó).
 */
@Composable
fun RegisterInfoScreen(
    state: RegisterInfoUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onAgreeToggle: () -> Unit,
    onSubmit: () -> Unit,
    onLogin: (String?) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val next = { focus.moveFocus(FocusDirection.Down); Unit }
    val submit = { keyboard?.hide(); focus.clearFocus(); onSubmit() }
    val loginLabel = stringResource(R.string.register_login)
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.register_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.register_intro),
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            GgTextField(
                label = stringResource(R.string.register_name),
                value = state.input.name,
                onValueChange = onNameChange,
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { next() }),
                errorText = fieldErrorText(state.nameError),
                modifier = Modifier.semantics { contentType = ContentType.PersonFullName },
            )
            GgTextField(
                label = stringResource(R.string.register_email),
                value = state.input.email,
                onValueChange = onEmailChange,
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { next() }),
                errorText = fieldErrorText(state.emailError),
                errorActionLabel = loginLabel.takeIf { state.emailError == RegisterFieldError.EmailTaken },
                onErrorAction = { onLogin(state.input.email.trim()) },
                modifier = Modifier.semantics { contentType = ContentType.EmailAddress },
            )
            GgTextField(
                label = stringResource(R.string.register_phone),
                value = state.input.phone,
                onValueChange = onPhoneChange,
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { next() }),
                errorText = fieldErrorText(state.phoneError),
                errorActionLabel = loginLabel.takeIf { state.phoneError == RegisterFieldError.PhoneTaken },
                onErrorAction = { onLogin(state.input.phone.trim()) },
                modifier = Modifier.semantics { contentType = ContentType.PhoneNumber },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PasswordField(
                    label = stringResource(R.string.register_password),
                    value = state.input.password,
                    onValueChange = onPasswordChange,
                    errorText = passwordErrorText(state.passwordError),
                    onImeAction = next,
                )
                if (state.passwordError == null) {
                    Text(
                        stringResource(R.string.register_password_hint),
                        style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.inkMuted,
                    )
                }
            }
            PasswordField(
                label = stringResource(R.string.forgot_new_confirm),
                value = state.input.confirm,
                onValueChange = onConfirmChange,
                errorText = passwordErrorText(state.confirmError),
                imeAction = ImeAction.Done,
                onImeAction = submit,
            )
            AgreeRow(state.agreed, onAgreeToggle, onOpenTerms, onOpenPrivacy)
            state.formError?.let { error ->
                ErrorBox(
                    stringResource(
                        when (error) {
                            RegisterFormError.NoConnection -> R.string.error_no_internet
                            RegisterFormError.Server -> R.string.error_server
                        },
                    ),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.register_submit),
                onClick = submit,
                enabled = state.canSubmit,
                loading = state.submitting,
                mutedWhenDisabled = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Liên kết cao 48 dp để đủ vùng chạm; kéo lên 14 dp để chữ cách nút đúng 14 như prototype.
            Row(
                Modifier.fillMaxWidth().offset(y = (-14).dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.register_have_account) + " ", style = GgTheme.typography.bodySm, color = colors.inkMuted)
                LinkText(loginLabel, onClick = { onLogin(null) })
            }
        }
    }
}

/**
 * Ô đánh dấu đồng ý (Register.dc.html): hộp 22 dp bo 6, viền 2 dp; đã chọn nền + viền `primary`, dấu ✓ trắng; chưa
 * chọn nền trắng viền `lineStrong`. Câu 13/20 có hai liên kết 600 `primary` gạch chân mở trang điều khoản / chính sách;
 * chạm phần còn lại của hàng thì đổi trạng thái.
 */
@Composable
private fun AgreeRow(agreed: Boolean, onToggle: () -> Unit, onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(6.dp)
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
    val terms = stringResource(R.string.register_terms)
    val privacy = stringResource(R.string.register_privacy)
    val template = stringResource(R.string.register_agree)
    val text = buildAnnotatedString {
        val (before, rest) = template.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val (middle, after) = rest.split("%2\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        append(before)
        withLink(LinkAnnotation.Clickable("terms", linkStyle) { onOpenTerms() }) { append(terms) }
        append(middle)
        withLink(LinkAnnotation.Clickable("privacy", linkStyle) { onOpenPrivacy() }) { append(privacy) }
        append(after)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = agreed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = GgSpacing.space1),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(shape)
                .background(if (agreed) colors.primary else colors.surfaceRaised)
                .border(2.dp, if (agreed) colors.primary else colors.lineStrong, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (agreed) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
        }
        Text(text, style = GgTheme.typography.bodySm, color = colors.ink)
    }
}

/** Dữ liệu mẫu của Register.dc.html. */
val RegisterSample = RegistrationInput("Nguyễn Văn An", "an.nguyen@gmail.com", "0912 345 678", "GGpower2026", "GGpower2026")

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun RegisterInfoPreview() {
    GgPowerTheme(darkTheme = false) {
        RegisterInfoScreen(RegisterInfoUiState(RegisterSample, agreed = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 1000, showBackground = true)
@Composable
private fun RegisterInfoErrorPreview() {
    GgPowerTheme(darkTheme = false) {
        RegisterInfoScreen(
            RegisterInfoUiState(
                RegisterSample.copy(email = "demo@ggp.vn", password = "abc"), agreed = false,
                emailError = RegisterFieldError.EmailTaken, passwordError = PasswordError.TooWeak,
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
