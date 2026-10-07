package vn.ggpower.inverter.ui.qrscan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.project.FakeAddProjectRepository
import vn.ggpower.inverter.domain.project.DeviceQr
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Trạng thái quyền camera của màn 18. */
enum class CameraAccess { Checking, Granted, Denied }

/**
 * Nối màn 18 với quyền camera: xin quyền ngay khi mở (đặc tả §3.18, §34); bị từ chối thì hiện hướng dẫn mở Cài đặt;
 * quay lại từ Cài đặt thì kiểm tra lại. Đọc được mã đúng thì rung nhẹ.
 */
@Composable
fun QrScanRoute(
    viewModel: QrScanViewModel,
    onClose: () -> Unit,
    onManualEntry: () -> Unit,
    onUseDevice: (serial: String) -> Unit,
    onContactSupport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var access by remember { mutableStateOf(if (granted()) CameraAccess.Granted else CameraAccess.Checking) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        access = if (ok) CameraAccess.Granted else CameraAccess.Denied
    }
    LaunchedEffect(Unit) {
        if (access != CameraAccess.Granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    LifecycleResumeEffect(Unit) {
        if (granted()) access = CameraAccess.Granted else if (asked && access == CameraAccess.Checking) access = CameraAccess.Denied
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) { viewModel.scanned.collect { haptic.performHapticFeedback(HapticFeedbackType.Confirm) } }
    QrScanScreen(
        state = state,
        access = access,
        onClose = onClose,
        onManualEntry = onManualEntry,
        onToggleTorch = viewModel::onToggleTorch,
        onRescan = viewModel::onRescan,
        onUseDevice = onUseDevice,
        onContactSupport = onContactSupport,
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        camera = { torchOn, onTorchAvailable ->
            QrCamera(onCode = viewModel::onCodeDetected, torchOn = torchOn, onTorchAvailable = onTorchAvailable)
        },
    )
}

/**
 * Màn 18 · Quét mã QR (QRScan.png; đặc tả §3.18; ADR-004: chỉ mở từ 14a). Nền tối; camera phủ cả màn, ngoài khung ngắm
 * làm tối (BO_SUNG §24); khung 260 dp bo 20 có 4 góc trắng dày 4 dài 36 và vạch quét xanh; câu hướng dẫn 15/22;
 * cuối màn nút đèn pin tròn 56 dp và nút trắng "Nhập số serial bằng tay". Đọc được mã: tấm kết quả trượt từ đáy.
 */
@Composable
fun QrScanScreen(
    state: QrScanUiState,
    access: CameraAccess,
    onClose: () -> Unit,
    onManualEntry: () -> Unit,
    onToggleTorch: () -> Unit,
    onRescan: () -> Unit,
    onUseDevice: (String) -> Unit,
    onContactSupport: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    camera: @Composable (torchOn: Boolean, onTorchAvailable: (Boolean) -> Unit) -> Unit = { _, _ -> },
) {
    val colors = GgTheme.colors
    DarkStatusBar()
    var frame by remember { mutableStateOf<Rect?>(null) }
    // null: chưa biết (chưa mở camera) → vẫn hiện nút như prototype; false: máy không có đèn pin → ẩn.
    var torchAvailable by remember { mutableStateOf<Boolean?>(null) }
    val cameraOn = access == CameraAccess.Granted
    Box(modifier.fillMaxSize().background(colors.qrBackground)) {
        if (cameraOn) camera(state.torchOn) { torchAvailable = it }
        // Làm tối phần ngoài khung ngắm (chỉ khi có hình camera phía sau).
        if (cameraOn) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val f = frame ?: return@drawBehind
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        addRoundRect(RoundRect(f, CornerRadius(20.dp.toPx())))
                    }
                    drawPath(path, colors.qrScrim)
                },
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = GgSpacing.space1, end = GgSpacing.space2), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cd_close), tint = colors.qrContent, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(GgSpacing.space1))
                Text(stringResource(R.string.qr_title), style = GgTheme.typography.screenTitle, color = colors.qrContent)
            }
            if (access == CameraAccess.Denied) {
                PermissionDenied(onOpenSettings, onManualEntry, Modifier.weight(1f))
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(start = GgSpacing.space5, end = GgSpacing.space5, top = 40.dp, bottom = GgSpacing.space6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GgSpacing.space6),
                ) {
                    Text(
                        stringResource(R.string.qr_hint),
                        style = GgTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                        color = colors.qrHint,
                        textAlign = TextAlign.Center,
                    )
                    ViewFinder(cameraOn, Modifier.onGloballyPositioned { frame = it.boundsInRoot() })
                    if (state.invalid) {
                        Text(
                            stringResource(R.string.qr_invalid),
                            style = GgTheme.typography.label.copy(lineHeight = 18.sp),
                            color = colors.qrContent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.danger)
                                .padding(horizontal = GgSpacing.space3, vertical = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (torchAvailable != false) TorchButton(state.torchOn, onToggleTorch)
                        Spacer(Modifier.weight(1f))
                        ManualButton(onManualEntry)
                    }
                }
            }
        }
        state.sheet?.let {
            ResultSheet(it, onRescan, onUseDevice, onContactSupport, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Màn tối: chữ thanh trạng thái màu sáng; trả lại như cũ khi rời màn. */
@Composable
private fun DarkStatusBar() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        val beforeNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = before
            controller.isAppearanceLightNavigationBars = beforeNav
        }
    }
}

/** Khung ngắm 260 dp bo 20: lòng trắng 6 % (khi chưa có camera), 4 góc trắng dày 4 dài 36, vạch quét xanh 2 dp có quầng. */
@Composable
private fun ViewFinder(cameraOn: Boolean, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val cd = stringResource(R.string.cd_qr_frame)
    Box(
        modifier
            .size(260.dp)
            .semantics { contentDescription = cd }
            .drawBehind {
                val r = 20.dp.toPx()
                val w = 4.dp.toPx()
                val arm = 36.dp.toPx()
                if (!cameraOn) drawRoundRect(colors.qrFrameFill, cornerRadius = CornerRadius(r))
                // Bốn góc: cung bo + hai cánh, vẽ bằng nét dày 4.
                val half = w / 2
                val stroke = Stroke(w)
                fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                    val p = Path().apply {
                        moveTo(x + sx * half, y + sy * arm)
                        lineTo(x + sx * half, y + sy * r)
                        quadraticTo(x + sx * half, y + sy * half, x + sx * r, y + sy * half)
                        lineTo(x + sx * arm, y + sy * half)
                    }
                    drawPath(p, colors.qrContent, style = stroke)
                }
                corner(0f, 0f, 1f, 1f)
                corner(size.width, 0f, -1f, 1f)
                corner(0f, size.height, 1f, -1f)
                corner(size.width, size.height, -1f, -1f)
                // Vạch quét ở giữa, cách hai bên 24.
                val y = 128.dp.toPx()
                val inset = 24.dp.toPx()
                val glow = 12.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, colors.ggGreen.copy(alpha = 0.35f), Color.Transparent),
                        startY = y + 1.dp.toPx() - glow, endY = y + 1.dp.toPx() + glow,
                    ),
                    topLeft = Offset(inset, y + 1.dp.toPx() - glow), size = Size(size.width - 2 * inset, 2 * glow),
                )
                drawRect(colors.ggGreen, topLeft = Offset(inset, y), size = Size(size.width - 2 * inset, 2.dp.toPx()))
            },
    )
}

/** Nút đèn pin tròn 56 dp: tắt — nền trắng 14 %, icon trắng; bật — nền trắng, icon `qrOnContent` (BO_SUNG §24). */
@Composable
private fun TorchButton(on: Boolean, onClick: () -> Unit) {
    val colors = GgTheme.colors
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (on) colors.qrContent else colors.qrButton)
            .clickable(role = Role.Switch, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_flash),
            contentDescription = stringResource(if (on) R.string.cd_torch_off else R.string.cd_torch_on),
            tint = if (on) colors.qrOnContent else colors.qrContent,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Nút "Nhập số serial bằng tay": cao 48, đệm 18, bo tròn hẳn, nền trắng; icon bàn phím 20 + chữ 14 600 `qrOnContent`. */
@Composable
private fun ManualButton(onClick: () -> Unit) {
    val colors = GgTheme.colors
    Row(
        Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.qrContent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, tint = colors.qrOnContent, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.qr_manual), style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp), color = colors.qrOnContent)
    }
}

/** Chưa có quyền camera (BO_SUNG §24): icon, tiêu đề, giải thích, nút "Mở Cài đặt" và "Nhập số serial bằng tay". */
@Composable
private fun PermissionDenied(onOpenSettings: () -> Unit, onManualEntry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = GgSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(colors.qrButton), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_camera_off), contentDescription = null, tint = colors.qrContent, modifier = Modifier.size(32.dp))
        }
        Text(
            stringResource(R.string.qr_denied_title),
            style = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
            color = colors.qrContent,
            textAlign = TextAlign.Center,
        )
        Text(stringResource(R.string.qr_denied_body), style = GgTheme.typography.bodySm, color = colors.qrHint, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.add_info_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
        ManualButton(onManualEntry)
    }
}

/**
 * Tấm kết quả (QRScan.dc.html): nền trắng bo trên 24, đệm 20 20 28, cách 12; tay nắm 40 × 4; hàng ô 44 dp bo 12
 * `primarySoft` có dấu ✓, tên thiết bị 16/22 600, "model · serial" monospace 12/16, chip "Mã hợp lệ"; nút "Dùng thiết bị
 * này"; chữ "Quét lại" 14 600 `primary` cao 44. Đang kiểm tra / lỗi: xem BO_SUNG §24.
 */
@Composable
private fun ResultSheet(
    sheet: ScanSheet,
    onRescan: () -> Unit,
    onUseDevice: (String) -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, shape)
            .clip(shape)
            .background(colors.surfaceRaised)
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space5, bottom = 28.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(CircleShape).background(colors.line))
        val problem = (sheet as? ScanSheet.Problem)?.problem
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (problem != null) colors.dangerSoft else colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    sheet is ScanSheet.Checking -> CircularProgressIndicator(Modifier.size(20.dp), color = colors.primary, strokeWidth = 2.dp)
                    problem != null -> Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.dangerText, modifier = Modifier.size(24.dp))
                    else -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (sheet) {
                        is ScanSheet.Checking -> stringResource(R.string.qr_checking)
                        is ScanSheet.Found -> sheet.device.name
                        is ScanSheet.Problem -> stringResource(
                            when (sheet.problem) {
                                ScanProblem.NotFound -> R.string.qr_problem_not_found
                                ScanProblem.OwnedByOther -> R.string.add_device_error_owned
                                ScanProblem.NoConnection -> R.string.error_no_internet
                                ScanProblem.Server -> R.string.error_server
                            },
                        )
                    },
                    style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = if (problem != null) colors.dangerText else colors.ink,
                )
                Text(
                    "${sheet.qr.model} · ${sheet.qr.serial}",
                    style = GgTheme.typography.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal),
                    color = colors.inkMuted,
                )
            }
            if (sheet is ScanSheet.Found) StatusChip(stringResource(R.string.qr_valid), StatusTone.Success)
        }
        when {
            sheet is ScanSheet.Found -> PrimaryButton(stringResource(R.string.qr_use_device), onClick = { onUseDevice(sheet.qr.serial) }, modifier = Modifier.fillMaxWidth())
            problem == ScanProblem.OwnedByOther -> PrimaryButton(stringResource(R.string.add_device_contact_support), onClick = onContactSupport, modifier = Modifier.fillMaxWidth())
            sheet is ScanSheet.Checking -> PrimaryButton(stringResource(R.string.qr_use_device), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            else -> Unit
        }
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).clickable(role = Role.Button, onClick = onRescan),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.qr_rescan), style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp), color = colors.primary)
        }
    }
}

private val SampleQr = DeviceQr("HYB5K", FakeAddProjectRepository.SAMPLE_SERIAL)

/** Trạng thái mẫu cho Preview và màn debug. */
fun qrScanSample(kind: String): QrScanUiState = when (kind) {
    "found" -> QrScanUiState(sheet = ScanSheet.Found(SampleQr, FakeAddProjectRepository.SAMPLE_DEVICE))
    "checking" -> QrScanUiState(sheet = ScanSheet.Checking(SampleQr))
    "owned" -> QrScanUiState(sheet = ScanSheet.Problem(SampleQr.copy(serial = FakeAddProjectRepository.OWNED_SERIAL), ScanProblem.OwnedByOther))
    "invalid" -> QrScanUiState(invalid = true)
    else -> QrScanUiState()
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun QrScanPreview() {
    GgPowerTheme(darkTheme = false) { QrScanScreen(qrScanSample(""), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun QrScanFoundPreview() {
    GgPowerTheme(darkTheme = false) { QrScanScreen(qrScanSample("found"), CameraAccess.Checking, {}, {}, {}, {}, {}, {}, {}) }
}
