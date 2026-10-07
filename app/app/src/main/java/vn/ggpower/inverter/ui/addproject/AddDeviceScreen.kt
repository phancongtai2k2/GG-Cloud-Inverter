package vn.ggpower.inverter.ui.addproject

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.DeviceIdentity
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun AddDeviceRoute(
    viewModel: AddProjectViewModel,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onContinue: () -> Unit,
    onContactSupport: () -> Unit,
) {
    val state by viewModel.device.collectAsStateWithLifecycle()
    AddDeviceScreen(state, onBack, onScanQr, viewModel::onSerialChange, viewModel::onCheck, onContinue, onContactSupport)
}

/** Ba bước của luồng Thêm dự án (14a–14c). */
@Composable
fun addProjectSteps() = listOf(
    stringResource(R.string.add_project_step_device),
    stringResource(R.string.add_project_step_info),
    stringResource(R.string.add_project_step_tariff),
)

/**
 * Màn 14a · Thêm dự án – Thiết bị (AddProject.png; đặc tả §3.14 bước 1). Quét QR (màn 18) hoặc nhập serial rồi
 * "Kiểm tra"; serial hợp lệ hiện thẻ tóm tắt và bật "Tiếp tục". Serial thuộc tài khoản khác: lỗi kèm "Liên hệ hỗ trợ".
 */
@Composable
fun AddDeviceScreen(
    state: AddDeviceUiState,
    onBack: () -> Unit,
    onScanQr: () -> Unit,
    onSerialChange: (String) -> Unit,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val check = { keyboard?.hide(); focus.clearFocus(); onCheck() }
    val fieldError = when (state.error) {
        SerialError.Empty -> stringResource(R.string.add_device_error_empty)
        SerialError.NotFound -> stringResource(R.string.add_device_error_not_found)
        SerialError.OwnedByOther -> stringResource(R.string.add_device_error_owned)
        else -> null
    }
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.add_project_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        ) {
            StepProgress(addProjectSteps(), current = 1)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(R.string.add_device_intro),
                    style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    color = colors.inkMuted,
                )
                PrimaryButton(stringResource(R.string.add_device_scan), onClick = onScanQr, icon = R.drawable.ic_qr, modifier = Modifier.fillMaxWidth())
                OrDivider(stringResource(R.string.add_device_or_manual))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2), verticalAlignment = Alignment.Bottom) {
                        GgTextField(
                            label = stringResource(R.string.add_device_serial),
                            value = state.serial,
                            onValueChange = onSerialChange,
                            size = FieldSize.Form,
                            monospace = true,
                            isError = fieldError != null,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                            ),
                            keyboardActions = KeyboardActions(onDone = { check() }),
                            modifier = Modifier.weight(1f),
                        )
                        CheckButton(state.checking, onClick = check)
                    }
                    if (fieldError != null) {
                        FieldError(
                            fieldError,
                            actionLabel = stringResource(R.string.add_device_contact_support).takeIf { state.error == SerialError.OwnedByOther },
                            onAction = onContactSupport,
                        )
                    } else {
                        Text(stringResource(R.string.add_device_serial_hint), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
                    }
                }
                when (state.error) {
                    SerialError.NoConnection -> ErrorBox(stringResource(R.string.error_no_internet))
                    SerialError.Server -> ErrorBox(stringResource(R.string.error_server))
                    else -> Unit
                }
                state.device?.let { DeviceCard(it) }
                PrimaryButton(
                    stringResource(R.string.add_project_continue),
                    onClick = onContinue,
                    enabled = state.canContinue,
                    mutedWhenDisabled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = GgSpacing.space1),
                )
            }
        }
    }
}

/** Dải "hoặc nhập tay": hai vạch 1 dp `line`, chữ 12 500 `inkMuted`, cách 12. */
@Composable
private fun OrDivider(text: String) {
    val colors = GgTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
        Text(text, style = GgTheme.typography.caption, color = colors.inkMuted)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
    }
}

/** Nút "Kiểm tra" (AddProject.dc.html): cao 48, đệm ngang 16, bo 10, nền trắng viền 1 dp `primary`, chữ 14 600 `primary`. */
@Composable
private fun CheckButton(checking: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Box(
        Modifier
            .height(48.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.primary, shape)
            .clickable(enabled = !checking, role = Role.Button, onClick = onClick)
            .padding(horizontal = GgSpacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        // Giữ bề rộng của chữ khi đang kiểm tra để nút không co lại.
        Text(
            stringResource(R.string.add_device_check),
            style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = if (checking) colors.surfaceRaised else colors.primary,
        )
        if (checking) CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
    }
}

/** Lỗi dưới ô serial (chữ đỏ 12/16) kèm liên kết tuỳ chọn, vùng chạm 48 dp. */
@Composable
private fun FieldError(text: String, actionLabel: String?, onAction: () -> Unit) {
    val colors = GgTheme.colors
    Column {
        Text(text, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = colors.dangerText)
        if (actionLabel != null) {
            Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction), contentAlignment = Alignment.CenterStart) {
                Text(
                    actionLabel,
                    style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = colors.primary,
                )
            }
        }
    }
}

/**
 * Thẻ thiết bị hợp lệ (AddProject.dc.html): đệm 14 16, cách 4; tên 15/22 600 + chip "Hợp lệ"; các dòng tối thiểu
 * 44 + đệm 8 hai đầu (CSS content-box), nhãn 13/18 `inkMuted`, giá trị 14/20 600 số đều canh phải, kẻ `cardBorder` giữa các dòng.
 */
@Composable
private fun DeviceCard(device: DeviceIdentity) {
    val colors = GgTheme.colors
    val rows = listOf(
        stringResource(R.string.add_device_model) to device.model,
        stringResource(R.string.add_device_rated_power) to GgFormat.trimmed(device.ratedPowerKw, 1) + " " + stringResource(R.string.unit_kw),
        stringResource(R.string.add_device_manufactured) to GgFormat.date(device.manufacturedOn),
    )
    GgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = GgSpacing.space4, vertical = 14.dp), verticalSpacing = GgSpacing.space1) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            Text(
                device.name,
                style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(R.string.add_device_valid), StatusTone.Success)
        }
        Column {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = colors.cardBorder)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = GgSpacing.space2).heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
                ) {
                    Text(label, style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp), color = colors.inkMuted, modifier = Modifier.weight(1f))
                    Text(
                        value,
                        style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                        color = colors.ink,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AddDevicePreview() {
    GgPowerTheme(darkTheme = false) {
        AddDeviceScreen(
            AddDeviceUiState(serial = FakeAddProjectRepository.SAMPLE_SERIAL, device = FakeAddProjectRepository.SAMPLE_DEVICE),
            {}, {}, {}, {}, {}, {},
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AddDeviceOwnedPreview() {
    GgPowerTheme(darkTheme = false) {
        AddDeviceScreen(AddDeviceUiState(serial = FakeAddProjectRepository.OWNED_SERIAL, error = SerialError.OwnedByOther), {}, {}, {}, {}, {}, {})
    }
}
