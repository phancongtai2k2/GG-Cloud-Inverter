package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Một dòng thay đổi: tên tham số và giá trị "cũ → mới" (BO_SUNG_v3.md §1). [label] trống: chỉ hiện [value] (ví dụ xoá chiến lược). */
data class ChangeLine(val label: String, val value: String)

/**
 * Khung dialog giữa màn (BO_SUNG_v3.md §1): bo `radius-lg`, nền `surface-raised`, bóng `shadow-overlay`, lề trong 20 dp.
 * [edgeToEdge]: rộng theo màn, cách mép 20 dp (Account__delete-dialog.png) thay cho độ rộng mặc định của hệ thống.
 */
@Composable
fun GgDialog(onDismiss: () -> Unit, edgeToEdge: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    if (edgeToEdge) {
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GgDialogSurface(Modifier.padding(horizontal = GgSpacing.space5), content = content)
        }
    } else {
        Dialog(onDismissRequest = onDismiss) { GgDialogSurface(content = content) }
    }
}

@Composable
private fun GgDialogSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(GgRadius.radiusLg)
    val colors = GgTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.surfaceRaised)
            .padding(GgSpacing.space5),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        content = content,
    )
}

/**
 * Hộp "Xác nhận thay đổi" dùng chung màn 16 và 16b (BO_SUNG_v3.md §1): tiêu đề `label` 600; mỗi dòng tên tham số
 * `body-sm` `ink-muted` trên, giá trị `body` 600 dưới; ghi chú `caption`; nút "Huỷ" · "Xác nhận" cao 48 dp.
 * [warning]: dòng `warning` trên đầu (đọc lại vì `CONFIG_STALE`); [info]: dòng `info` dưới ghi chú.
 */
@Composable
fun ConfirmChangesDialog(
    lines: List<ChangeLine>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    warning: String? = null,
    info: String? = null,
) {
    GgDialog(onDismiss) { ConfirmChangesContent(lines, onConfirm, onDismiss, warning, info) }
}

@Composable
private fun ColumnScope.ConfirmChangesContent(
    lines: List<ChangeLine>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    warning: String?,
    info: String?,
) {
    val colors = GgTheme.colors
    val typo = GgTheme.typography
    Text(
        stringResource(R.string.confirm_title),
        style = typo.label,
        color = colors.ink,
        modifier = Modifier.semantics { heading() },
    )
    if (warning != null) Text(warning, style = typo.caption, color = colors.warning)
    Column(
        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space3),
    ) {
        lines.forEach { line ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (line.label.isNotBlank()) Text(line.label, style = typo.bodySm, color = colors.inkMuted)
                Text(line.value, style = typo.body.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        Text(stringResource(R.string.confirm_note), style = typo.caption, color = colors.inkMuted)
        if (info != null) Text(info, style = typo.caption, color = colors.info)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        SecondaryButton(stringResource(R.string.action_cancel), onClick = onDismiss, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.action_confirm), onClick = onConfirm, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
    }
}

/** Dialog thông báo một nút (BO_SUNG_v3.md §2: "Không chắc thiết bị đang chạy giá trị nào…" · "Đã hiểu"). */
@Composable
fun NoticeDialog(text: String, buttonText: String, onDismiss: () -> Unit) {
    GgDialog(onDismiss) {
        Text(text, style = GgTheme.typography.body, color = GgTheme.colors.ink)
        PrimaryButton(buttonText, onClick = onDismiss, size = ButtonSize.Medium, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFF808080)
@Composable
private fun ConfirmChangesPreview() {
    GgPowerTheme(darkTheme = false) {
        GgDialogSurface(Modifier.padding(GgSpacing.space6)) {
            ConfirmChangesContent(
                lines = listOf(
                    ChangeLine("Chế độ vận hành", "Tự tiêu thụ tối đa → Ưu tiên sạc pin"),
                    ChangeLine("SOC dự phòng", "20 % → 30 %"),
                ),
                onConfirm = {}, onDismiss = {},
                warning = "Cấu hình vừa được thay đổi ở nơi khác. Hãy xem lại trước khi xác nhận.",
                info = null,
            )
        }
    }
}
