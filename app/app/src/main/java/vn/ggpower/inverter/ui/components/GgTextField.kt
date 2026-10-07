package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Cỡ ô nhập. [Medium]: sheet 16b (ô 44 dp, đệm 12, chữ 14, nhãn 12/16, cách 6).
 * [Large]: Đăng nhập (Login.dc.html: ô 52 dp, đệm 16, chữ 15, nhãn 13/16, cách 8).
 * [Form]: biểu mẫu đợt 3 — Quên mật khẩu, Đăng ký, Thêm dự án, Tài khoản (ô 48 dp, đệm 14, chữ 14, nhãn 13/16, cách 6).
 * [Ticket]: Yêu cầu hỗ trợ màn 8 (Support.dc.html: ô 46 dp, đệm 14, chữ 14, nhãn 13/16, cách 6).
 * [tabular]: số đều (font-variant-numeric: tabular-nums) — chỉ ô giờ / số của sheet 16b dùng.
 */
enum class FieldSize(val height: Dp, val padding: Dp, val fontSize: TextUnit, val labelSize: TextUnit, val gap: Dp, val tabular: Boolean) {
    Medium(44.dp, 12.dp, 14.sp, 12.sp, 6.dp, tabular = true),
    Large(52.dp, 16.dp, 15.sp, 13.sp, 8.dp, tabular = false),
    Form(48.dp, 14.dp, 14.sp, 13.sp, 6.dp, tabular = false),
    Ticket(46.dp, 14.dp, 14.sp, 13.sp, 6.dp, tabular = false),
}

/**
 * Ô nhập có nhãn: nền `surfaceRaised`, bo 10 dp, viền 1 dp `lineStrong` (lỗi: `dangerText`).
 * [placeholder] màu `inkMuted`; [trailing] là nút 48 dp ở mép phải (ví dụ hiện/ẩn mật khẩu);
 * [errorText] hiện dưới ô, chữ 12/16 `dangerText` (đặc tả §3.1: báo lỗi ngay dưới ô nhập).
 */
@Composable
fun GgTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: FieldSize = FieldSize.Medium,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    /** Ghi chú xám 12/16 dưới ô khi không có lỗi (Register.dc.html "Tối thiểu 8 ký tự…"). */
    helperText: String? = null,
    /** Liên kết ngay sau dòng lỗi (đặc tả §3.13: email / số đã đăng ký → "Đăng nhập"). */
    errorActionLabel: String? = null,
    onErrorAction: () -> Unit = {},
    /** Chữ monospace cho số serial (AddProject.dc.html `ui-monospace`). */
    monospace: Boolean = false,
    /**
     * Ô nhiều dòng cao cố định (textarea "Mô tả chi tiết" màn 8: cao 96, đệm 12 14, dòng 20): chữ bắt đầu từ trên,
     * cuộn bên trong khi dài hơn ô. null = ô một dòng cao [FieldSize.height].
     */
    multilineHeight: Dp? = null,
) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    val showError = isError || errorText != null
    val textStyle = GgTheme.typography.body.copy(
        fontSize = size.fontSize, lineHeight = 20.sp, color = colors.ink, fontFeatureSettings = if (size.tabular) "tnum" else null,
    ).let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(size.gap)) {
        Text(
            label,
            style = GgTheme.typography.caption.copy(fontSize = size.labelSize, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        // Giữ con trỏ / vùng soạn của IME khi màn nhận nguyên chữ vừa gõ; khi màn tự sửa chữ (ví dụ ô giờ tự chèn ":")
        // thì đưa con trỏ về cuối, nếu không chữ số gõ tiếp sẽ chèn vào giữa ("1230" thành "12:03").
        // Ô không có focus mà chữ bị đổi từ ngoài (ví dụ 14b tự điền địa chỉ) thì đặt con trỏ ở đầu để thấy phần đầu chữ.
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        var typed by remember { mutableStateOf(TextFieldValue(value, TextRange(0))) }
        val shown = if (typed.text == value) typed else TextFieldValue(value, TextRange(if (focused) value.length else 0))
        BasicTextField(
            value = shown,
            onValueChange = { next ->
                typed = next
                if (next.text != value) onValueChange(next.text)
            },
            enabled = enabled,
            singleLine = singleLine && multilineHeight == null,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            interactionSource = interaction,
            modifier = Modifier.semantics {
                contentDescription = label
                if (errorText != null) error(errorText)
            },
            decorationBox = { inner ->
                val multiline = multilineHeight != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(multilineHeight ?: size.height)
                        .clip(shape)
                        .background(colors.surfaceRaised)
                        .border(1.dp, if (showError) colors.dangerText else colors.lineStrong, shape)
                        .padding(start = size.padding, end = if (trailing != null) 2.dp else size.padding)
                        .then(if (multiline) Modifier.padding(vertical = GgSpacing.space3) else Modifier),
                    verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = textStyle.copy(color = colors.inkMuted), maxLines = if (multiline) Int.MAX_VALUE else 1)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        if (errorText != null) {
            if (errorActionLabel == null) {
                Text(errorText, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = colors.dangerText)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(errorText + " ", style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = colors.dangerText)
                    Text(
                        errorActionLabel,
                        style = GgTheme.typography.caption.copy(lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                        color = colors.primary,
                        modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onErrorAction),
                    )
                }
            }
        } else if (helperText != null) {
            Text(helperText, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal), color = colors.inkMuted)
        }
    }
}

/**
 * Hộp lỗi (sheet 16b): nền `dangerSoft`, chữ 13/18 500 `dangerText`, icon tròn có dấu chấm than, đệm 10 / 12 dp,
 * bo 10 dp. TalkBack đọc ngay khi hộp xuất hiện.
 */
@Composable
fun ErrorBox(text: String, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GgRadius.radiusMd))
            .background(colors.dangerSoft)
            .padding(horizontal = GgSpacing.space3, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        Icon(painterResource(R.drawable.ic_error), contentDescription = null, tint = colors.dangerText, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp, fontWeight = FontWeight.Medium),
            color = colors.dangerText,
        )
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun GgTextFieldPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgTextField("Giờ bắt đầu", "22:00", {}, Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                GgTextField("Giờ kết thúc", "06:00", {}, Modifier.weight(1f), isError = true)
            }
            ErrorBox("Trùng với Xả 1 (09:00 – 12:00). Các khung không được chồng lấn.")
        }
    }
}
