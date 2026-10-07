package vn.ggpower.inverter.ui.addproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.PROJECT_NAME_MAX_LENGTH
import vn.ggpower.inverter.domain.project.PROJECT_TIME_ZONES
import vn.ggpower.inverter.domain.project.ProjectInfoError
import vn.ggpower.inverter.domain.project.ProjectInfoInput
import vn.ggpower.inverter.domain.project.SiteType
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgDialog
import vn.ggpower.inverter.ui.components.rememberImageThumbnail
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Nối 14b với quyền vị trí và trình chọn ảnh của Android. Nút định vị: đã có quyền thì lấy vị trí ngay; chưa có thì
 * hiện hộp giải thích (đặc tả §34) rồi mới xin quyền của hệ thống.
 */
@Composable
fun AddInfoRoute(viewModel: AddProjectViewModel, onBack: () -> Unit, onContinue: () -> Unit) {
    val state by viewModel.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var rationale by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.infoDone.collect { onContinue() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onLocate() else viewModel.onLocationDenied()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onInfoChange { it.copy(photoUri = uri.toString()) }
    }
    AddInfoScreen(
        state = state,
        onBack = onBack,
        onChange = viewModel::onInfoChange,
        onLocate = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.onLocate() else rationale = true
        },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onContinue = viewModel::onInfoContinue,
    )
    if (rationale) {
        LocationRationale(
            onDismiss = { rationale = false },
            onAllow = { rationale = false; permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        )
    }
}

/** Câu lỗi dưới ô của 14b (BO_SUNG §22). */
@Composable
private fun infoErrorText(error: ProjectInfoError?): String? = when (error) {
    null -> null
    ProjectInfoError.NameEmpty -> stringResource(R.string.add_info_error_name_empty)
    ProjectInfoError.NameTooLong -> stringResource(R.string.add_info_error_name_long, PROJECT_NAME_MAX_LENGTH)
    ProjectInfoError.AddressEmpty -> stringResource(R.string.add_info_error_address_empty)
    ProjectInfoError.DateInFuture -> stringResource(R.string.add_info_error_date_future)
    ProjectInfoError.KwpEmpty -> stringResource(R.string.add_info_error_kwp_empty)
    ProjectInfoError.KwpInvalid -> stringResource(R.string.add_info_error_kwp_invalid)
}

/**
 * Màn 14b · Thêm dự án – Thông tin dự án (AddProjectInfo.png; đặc tả §3.14 bước 2). Các khối cách 12; Múi giờ và
 * Ngày lắp đặt chia đôi một hàng (cách 10); hai nút cuối Quay lại (1 phần) · Tiếp tục (2 phần) cách 10.
 */
@Composable
fun AddInfoScreen(
    state: AddInfoUiState,
    onBack: () -> Unit,
    onChange: ((ProjectInfoInput) -> ProjectInfoInput) -> Unit,
    onLocate: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickPhoto: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val input = state.input
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.add_project_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepProgress(addProjectSteps(), current = 2)
            Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
                GgTextField(
                    label = stringResource(R.string.add_info_name),
                    value = input.name,
                    onValueChange = { v -> onChange { it.copy(name = v) } },
                    size = FieldSize.Form,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    errorText = infoErrorText(state.nameError),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel(stringResource(R.string.add_info_site_type))
                    val home = stringResource(R.string.add_info_site_home)
                    val factory = stringResource(R.string.add_info_site_factory)
                    SegmentedControl(
                        options = listOf(home, factory),
                        selectedIndex = if (input.siteType == SiteType.Home) 0 else 1,
                        onSelect = { i -> onChange { it.copy(siteType = if (i == 0) SiteType.Home else SiteType.Factory) } },
                        groupLabel = stringResource(R.string.add_info_site_type_group),
                    )
                }
                AddressField(state, onChange, onLocate, onOpenSettings)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeZoneField(input.timeZone, { tz -> onChange { it.copy(timeZone = tz) } }, Modifier.weight(1f))
                    DateField(input.installedOn, infoErrorText(state.dateError), { d -> onChange { it.copy(installedOn = d) } }, Modifier.weight(1f))
                }
                GgTextField(
                    label = stringResource(R.string.add_info_kwp),
                    value = input.pvKwp,
                    onValueChange = { v -> onChange { it.copy(pvKwp = v) } },
                    size = FieldSize.Form,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    errorText = infoErrorText(state.kwpError),
                    helperText = stringResource(R.string.add_info_kwp_hint),
                )
                PhotoField(input.photoUri, onPickPhoto, onRemove = { onChange { it.copy(photoUri = null) } })
                Row(Modifier.padding(top = GgSpacing.space1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(stringResource(R.string.add_project_back), onClick = onBack, modifier = Modifier.weight(1f))
                    PrimaryButton(
                        stringResource(R.string.add_project_continue),
                        onClick = { focus.clearFocus(); onContinue() },
                        modifier = Modifier.weight(2f),
                    )
                }
            }
        }
    }
}

/** Nhãn ô 13/16 600 (cùng kiểu nhãn của [GgTextField]). */
@Composable
private fun FieldLabel(text: String) {
    Text(text, style = GgTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = GgTheme.colors.ink)
}

/** Khung ô 48 dp bo 10 nền trắng viền `lineStrong` (lỗi: `dangerText`) dùng cho ô chọn và nút định vị. */
private fun Modifier.fieldBox(border: Color, bg: Color) =
    this.height(48.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(bg).border(1.dp, border, RoundedCornerShape(GgRadius.radiusMd))

/**
 * Địa chỉ: ô nhập + nút định vị 48 dp (viền `lineStrong`, icon `primary`), dưới cả hàng là lỗi / ghi chú vị trí /
 * dòng gợi ý "Toạ độ dùng để tra cứu thời tiết và bức xạ".
 */
@Composable
private fun AddressField(
    state: AddInfoUiState,
    onChange: ((ProjectInfoInput) -> ProjectInfoInput) -> Unit,
    onLocate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = GgTheme.colors
    val error = infoErrorText(state.addressError)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2), verticalAlignment = Alignment.Bottom) {
            GgTextField(
                label = stringResource(R.string.add_info_address),
                value = state.input.address,
                onValueChange = { v -> onChange { it.copy(address = v) } },
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .fieldBox(colors.lineStrong, colors.surfaceRaised)
                    .clickable(enabled = !state.locating, role = Role.Button, onClick = onLocate),
                contentAlignment = Alignment.Center,
            ) {
                if (state.locating) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.ic_locate), contentDescription = stringResource(R.string.cd_locate), tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        val note = state.locateNote
        when {
            error != null -> Text(error, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = colors.dangerText)
            note is LocateNote.Found -> Text(
                stringResource(
                    R.string.add_info_locate_found,
                    GgFormat.number(note.coordinates.latitude, 4),
                    GgFormat.number(note.coordinates.longitude, 4),
                ),
                style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"),
                color = colors.primary,
            )
            note == LocateNote.Denied -> Column {
                Text(stringResource(R.string.add_info_locate_denied), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.dangerText)
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onOpenSettings), contentAlignment = Alignment.CenterStart) {
                    Text(
                        stringResource(R.string.add_info_open_settings),
                        style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                        color = colors.primary,
                    )
                }
            }
            note == LocateNote.Disabled -> Text(stringResource(R.string.add_info_locate_disabled), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.dangerText)
            note == LocateNote.Failed -> Text(stringResource(R.string.add_info_locate_failed), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.dangerText)
            else -> Text(stringResource(R.string.add_info_address_hint), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
    }
}

/** Múi giờ: ô chọn 48 dp chữ 13, mũi tên xuống 16 dp `inkMuted`; chạm mở danh sách [PROJECT_TIME_ZONES]. */
@Composable
private fun TimeZoneField(value: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(stringResource(R.string.add_info_time_zone))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .fieldBox(colors.lineStrong, colors.surfaceRaised)
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(value, style = GgTheme.typography.body.copy(fontSize = 13.sp, lineHeight = 20.sp), color = colors.ink, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surfaceRaised) {
                PROJECT_TIME_ZONES.forEach { tz ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                tz,
                                style = GgTheme.typography.bodySm.copy(fontWeight = if (tz == value) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (tz == value) colors.primary else colors.ink,
                            )
                        },
                        onClick = { onSelect(tz); open = false },
                    )
                }
            }
        }
    }
}

/** Ngày lắp đặt: ô 48 dp chữ 14 dạng dd/MM/yyyy; chạm mở hộp chọn ngày, không chọn được ngày sau hôm nay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: LocalDate, error: String?, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    var open by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(stringResource(R.string.add_info_installed_on))
        Box(
            Modifier
                .fillMaxWidth()
                .fieldBox(if (error != null) colors.dangerText else colors.lineStrong, colors.surfaceRaised)
                .clickable(role = Role.Button) { open = true }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(GgFormat.date(value), style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"), color = colors.ink)
        }
        if (error != null) Text(error, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = colors.dangerText)
    }
    if (open) {
        val today = remember { LocalDate.now() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        val pickerColors = DatePickerDefaults.colors(
            containerColor = colors.surfaceRaised,
            titleContentColor = colors.inkMuted,
            headlineContentColor = colors.ink,
            weekdayContentColor = colors.inkMuted,
            navigationContentColor = colors.ink,
            yearContentColor = colors.ink,
            currentYearContentColor = colors.primary,
            selectedYearContainerColor = colors.primary,
            selectedYearContentColor = colors.onPrimary,
            dayContentColor = colors.ink,
            disabledDayContentColor = colors.inkMuted.copy(alpha = 0.38f),
            selectedDayContainerColor = colors.primary,
            selectedDayContentColor = colors.onPrimary,
            todayContentColor = colors.primary,
            todayDateBorderColor = colors.primary,
            dividerColor = colors.cardBorder,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            colors = pickerColors,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_choose), color = colors.primary) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel), color = colors.inkMuted) } },
        ) {
            DatePicker(
                state = state,
                colors = pickerColors,
                showModeToggle = false,
                title = { Text(stringResource(R.string.add_info_installed_on_title), style = GgTheme.typography.label, color = colors.inkMuted, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                headline = {
                    val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: value
                    Text(
                        GgFormat.date(picked),
                        style = GgTheme.typography.metric,
                        color = colors.ink,
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                    )
                },
            )
        }
    }
}

/**
 * Ảnh công trình: chưa chọn — khung 72 dp viền nét đứt `lineStrong`, icon ảnh + "Thêm ảnh (không bắt buộc)" 13 600;
 * đã chọn — ảnh xem trước cao 120 dp bo 10, nút ✕ tròn góc phải để bỏ ảnh; chạm ảnh để chọn ảnh khác (BO_SUNG §22).
 */
@Composable
private fun PhotoField(uri: String?, onPick: () -> Unit, onRemove: () -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(stringResource(R.string.add_info_photo))
        if (uri == null) {
            val border = colors.lineStrong
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(shape)
                    .drawBehind {
                        drawRoundRect(
                            border,
                            cornerRadius = CornerRadius(GgRadius.radiusMd.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                    .clickable(role = Role.Button, onClick = onPick),
                horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_image), contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.add_info_photo_add), style = GgTheme.typography.label, color = colors.ink)
            }
        } else {
            val bitmap = rememberImageThumbnail(uri)
            val cd = stringResource(R.string.cd_project_photo)
            Box(Modifier.fillMaxWidth().height(120.dp).clip(shape).background(colors.surfaceSunken).clickable(role = Role.Button, onClick = onPick)) {
                bitmap?.let { Image(it, contentDescription = cd, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(colors.surfaceRaised), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cd_remove_photo), tint = colors.ink, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Hộp giải thích trước khi xin quyền vị trí (đặc tả §34). */
@Composable
private fun LocationRationale(onDismiss: () -> Unit, onAllow: () -> Unit) {
    val colors = GgTheme.colors
    GgDialog(onDismiss = onDismiss) {
        Text(stringResource(R.string.add_info_locate_rationale_title), style = GgTheme.typography.label.copy(fontSize = 16.sp, lineHeight = 22.sp), color = colors.ink)
        Text(stringResource(R.string.add_info_locate_rationale_body), style = GgTheme.typography.bodySm, color = colors.inkMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.add_info_later), onClick = onDismiss, modifier = Modifier.weight(1f), size = ButtonSize.Medium)
            PrimaryButton(stringResource(R.string.add_project_continue), onClick = onAllow, modifier = Modifier.weight(1f), size = ButtonSize.Medium)
        }
    }
}

/** Dữ liệu mẫu AddProjectInfo.dc.html. */
val AddInfoSample = ProjectInfoInput(
    name = "Nhà của bạn", address = "Xuân Quan, Văn Giang, Hưng Yên", installedOn = LocalDate.of(2026, 9, 26), pvKwp = "6,5",
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AddInfoPreview() {
    GgPowerTheme(darkTheme = false) { AddInfoScreen(AddInfoUiState(AddInfoSample), {}, {}, {}, {}, {}, {}) }
}
