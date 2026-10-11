package vn.ggpower.inverter.ui.account

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.account.FakeProfileRepository
import vn.ggpower.inverter.domain.account.ProfileNameError
import vn.ggpower.inverter.ui.components.Avatar
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.DangerButton
import vn.ggpower.inverter.ui.components.DangerTextButton
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgDialog
import vn.ggpower.inverter.ui.components.GgSnackbar
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PasswordField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Màn 15 · Thông tin tài khoản: nối ViewModel với điều hướng. */
@Composable
fun AccountRoute(
    onLeave: () -> Unit,
    onChangeEmail: () -> Unit,
    onChangePhone: () -> Unit,
    onChangePassword: () -> Unit,
    /** Đã gửi yêu cầu xoá tài khoản: đăng xuất và về Đăng nhập. */
    onDeleted: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val leave by rememberUpdatedState(onLeave)
    val deleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(viewModel) {
        viewModel.events.collect {
            when (it) {
                AccountEvent.Leave -> leave()
                AccountEvent.Deleted -> deleted()
            }
        }
    }
    // Chỉ chọn từ thư viện (người dùng chốt 03/10/2026; chụp mới để task sau — OPEN_QUESTIONS).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAvatarPicked(uri.toString())
    }
    AccountScreen(
        state = state,
        actions = AccountActions(
            onBack = viewModel::onBack,
            onPickAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onNameChange = viewModel::onNameChange,
            onChangeEmail = onChangeEmail,
            onChangePhone = onChangePhone,
            onChangePassword = onChangePassword,
            onSave = viewModel::onSave,
            onSnackbarShown = viewModel::onSnackbarShown,
            onDiscardCancel = viewModel::onDiscardCancel,
            onDiscardConfirm = viewModel::onDiscardConfirm,
            onDeleteOpen = viewModel::onDeleteOpen,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onDeletePasswordChange = viewModel::onDeletePasswordChange,
            onDeleteConfirm = viewModel::onDeleteConfirm,
        ),
    )
}

class AccountActions(
    val onBack: () -> Unit = {},
    val onPickAvatar: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onChangeEmail: () -> Unit = {},
    val onChangePhone: () -> Unit = {},
    val onChangePassword: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onSnackbarShown: (Long) -> Unit = {},
    val onDiscardCancel: () -> Unit = {},
    val onDiscardConfirm: () -> Unit = {},
    val onDeleteOpen: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onDeletePasswordChange: (String) -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
)

/**
 * Màn 15 · Thông tin tài khoản (Account.png, Account__delete-dialog.png; đặc tả §3.15; Thay đổi 0.6 §7: không có mục
 * Ngôn ngữ; BO_SUNG §24). Lề 20, các khối cách 12; thanh đáy viền trên chứa nút "Lưu thay đổi" chỉ bật khi có thay đổi.
 */
@Composable
fun AccountScreen(state: AccountUiState, actions: AccountActions, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val content = state as? AccountUiState.Content
    BackHandler(enabled = content != null && (content.hasChanges || content.saving)) { actions.onBack() }
    Box(modifier.fillMaxSize().background(colors.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ScreenHeader(
                title = stringResource(R.string.screen_account),
                onBack = actions.onBack,
                backDescription = stringResource(R.string.cd_back),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space4),
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            ) {
                if (content != null) Body(content, actions) else LoadingBody()
            }
            SaveBar(content, actions.onSave)
        }
        val snackbar = content?.snackbar
        if (snackbar != null) {
            val shown by rememberUpdatedState(actions.onSnackbarShown)
            LaunchedEffect(snackbar.id) {
                delay(3_000)
                shown(snackbar.id)
            }
            GgSnackbar(
                when (snackbar) {
                    is AccountSnackbar.Saved -> stringResource(R.string.account_saved)
                    is AccountSnackbar.SaveFailed -> stringResource(if (snackbar.noConnection) R.string.error_no_internet else R.string.error_server)
                },
                if (snackbar is AccountSnackbar.Saved) StatusTone.Success else StatusTone.Danger,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = 96.dp),
            )
        }
    }
    if (content?.discardOpen == true) DiscardDialog(actions)
    content?.delete?.let { DeleteDialog(it, actions) }
}

@Composable
private fun Body(c: AccountUiState.Content, actions: AccountActions) {
    val colors = GgTheme.colors
    AvatarPicker(c.draft.name, c.draft.avatarUri, actions.onPickAvatar)
    GgCard(contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = GgSpacing.space3)) {
        GgTextField(
            label = stringResource(R.string.account_name),
            value = c.draft.name,
            onValueChange = actions.onNameChange,
            size = FieldSize.Form,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            errorText = when (c.nameError) {
                ProfileNameError.Empty -> stringResource(R.string.register_error_name_empty)
                null -> null
            },
        )
    }
    GgCard(contentPadding = PaddingValues(0.dp)) {
        LinkRow(R.drawable.ic_mail, stringResource(R.string.account_email), c.saved.email, actions.onChangeEmail)
        LinkRow(R.drawable.ic_phone, stringResource(R.string.account_phone), c.saved.phone, actions.onChangePhone)
        LinkRow(R.drawable.ic_lock, stringResource(R.string.account_security), stringResource(R.string.account_change_password), actions.onChangePassword)
    }
    DangerTextButton(stringResource(R.string.account_delete), onClick = actions.onDeleteOpen)
}

/**
 * Avatar 88 dp, chữ viết tắt 28/700; huy hiệu máy ảnh 32 dp nền `primary` viền 3 dp màu nền màn, lệch ra ngoài 2 dp.
 * Dòng gợi ý 12/16 `inkMuted`. Khối đệm 8 trên dưới, cách 8.
 */
@Composable
private fun AvatarPicker(name: String, imageUri: String?, onPick: () -> Unit) {
    val colors = GgTheme.colors
    val cd = stringResource(R.string.cd_change_avatar)
    Column(
        Modifier.fillMaxWidth().padding(vertical = GgSpacing.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        Box(Modifier.size(88.dp).semantics(mergeDescendants = true) { contentDescription = cd }) {
            Avatar(
                name,
                imageUri,
                size = 88.dp,
                textStyle = GgTheme.typography.body.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onPick),
            )
            CameraBadge(Modifier.align(Alignment.BottomEnd).offset(2.dp, 2.dp))
        }
        Text(stringResource(R.string.account_avatar_hint), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
    }
}

/** Huy hiệu máy ảnh ở góc phải dưới avatar. */
@Composable
private fun CameraBadge(modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Box(
        modifier
            .size(32.dp)
            .border(3.dp, colors.surface, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(colors.primaryFill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Dòng mở màn con: cao tối thiểu 56, đệm 6 16, cách 12; icon 20, nhãn 12/16 `inkMuted`, giá trị 14/20 600, mũi tên 18. */
@Composable
private fun LinkRow(@DrawableRes icon: Int, label: String, value: String, onClick: () -> Unit) {
    val colors = GgTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = GgSpacing.space4, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
            Text(value, style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = colors.ink)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp))
    }
}

/** Đang tải hồ sơ: khung giữ chỗ cùng bố cục (avatar, thẻ họ tên, thẻ ba dòng). */
@Composable
private fun LoadingBody() {
    val colors = GgTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = GgSpacing.space2), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(colors.surfaceSunken))
        Box(Modifier.padding(top = GgSpacing.space2).size(240.dp, 16.dp))
    }
    Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceSunken))
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceSunken))
}

/** Thanh đáy: đệm 12 20 24, viền trên 1 dp `cardBorder`; nút 52 dp, khoá nhạt khi chưa có thay đổi. */
@Composable
private fun SaveBar(content: AccountUiState.Content?, onSave: () -> Unit) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
        PrimaryButton(
            stringResource(R.string.account_save),
            onClick = { focus.clearFocus(); onSave() },
            enabled = content?.hasChanges == true,
            loading = content?.saving == true,
            mutedWhenDisabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space3, bottom = GgSpacing.space6),
        )
    }
}

/** Hộp "Bỏ thay đổi?" (BO_SUNG §4, dùng chung câu chữ với 16b). */
@Composable
private fun DiscardDialog(actions: AccountActions) {
    GgDialog(onDismiss = actions.onDiscardCancel) {
        Text(
            stringResource(R.string.tou_discard_title),
            style = GgTheme.typography.label.copy(fontSize = 16.sp, lineHeight = 22.sp),
            color = GgTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.tou_discard_body), style = GgTheme.typography.body, color = GgTheme.colors.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.tou_discard_stay), onClick = actions.onDiscardCancel, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
            DangerTextButton(stringResource(R.string.tou_discard_confirm), onClick = actions.onDiscardConfirm, icon = null, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Hộp xoá tài khoản (Account__delete-dialog.png): tiêu đề 18/26 700, nội dung 13/20 `inkMuted`, ô mật khẩu có nút mắt,
 * nút "Huỷ" · "Xoá tài khoản" (nền `danger`) cao 48 cách 10; các khối cách 12. Đang gửi thì không đóng được.
 */
@Composable
private fun DeleteDialog(d: DeleteDialogState, actions: AccountActions) {
    val colors = GgTheme.colors
    GgDialog(onDismiss = actions.onDeleteDismiss, edgeToEdge = true) {
        Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Text(
                stringResource(R.string.account_delete_title),
                style = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.account_delete_body),
                style = GgTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
                color = colors.inkMuted,
            )
            PasswordField(
                label = stringResource(R.string.account_delete_password),
                value = d.password,
                onValueChange = actions.onDeletePasswordChange,
                errorText = when (d.error) {
                    DeleteError.WrongPassword -> stringResource(R.string.account_delete_wrong_password)
                    DeleteError.NoConnection -> stringResource(R.string.error_no_internet)
                    DeleteError.Server -> stringResource(R.string.error_server)
                    null -> null
                },
                imeAction = ImeAction.Done,
                onImeAction = actions.onDeleteConfirm,
                newPassword = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    stringResource(R.string.account_delete_cancel),
                    onClick = actions.onDeleteDismiss,
                    enabled = !d.submitting,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
                DangerButton(
                    stringResource(R.string.account_delete),
                    onClick = actions.onDeleteConfirm,
                    enabled = d.canSubmit,
                    loading = d.submitting,
                    size = ButtonSize.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Trạng thái mẫu cho Preview và màn debug (dữ liệu Account.dc.html). */
fun accountSample(
    dirty: Boolean = false,
    delete: DeleteDialogState? = null,
    nameError: Boolean = false,
): AccountUiState.Content {
    val saved = FakeProfileRepository.SAMPLE
    val base = AccountUiState.Content(saved, delete = delete)
    return when {
        nameError -> base.copy(draft = base.draft.copy(name = ""), nameError = ProfileNameError.Empty)
        dirty -> base.copy(draft = base.draft.copy(name = "Michael Demo Nguyễn"))
        else -> base
    }
}

@Composable
private fun PreviewAccount(state: AccountUiState) {
    GgPowerTheme(darkTheme = false) { AccountScreen(state, AccountActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AccountPreview() = PreviewAccount(accountSample())

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AccountDirtyPreview() = PreviewAccount(accountSample(dirty = true))

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AccountLoadingPreview() = PreviewAccount(AccountUiState.Loading)
