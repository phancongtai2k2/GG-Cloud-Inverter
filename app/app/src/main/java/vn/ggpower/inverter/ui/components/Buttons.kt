package vn.ggpower.inverter.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Chiều cao nút: 52 dp (Đăng nhập, Login.dc.html) hoặc 48 dp (Huỷ / Lưu trong sheet 16b). */
enum class ButtonSize(val height: Dp) { Large(52.dp), Medium(48.dp) }

/** Chữ nút 15/600 (Login, sheet 16b). */
private val buttonText: TextStyle
    @Composable get() = GgTheme.typography.label.copy(fontSize = 15.sp, lineHeight = 20.sp)

/**
 * Nút chính: nền `primary`, chữ trắng, bo 10 dp. Bị khoá: nền #c9d1cb (`disabledFill`).
 * [loading]: vòng xoay thay cho chữ, không bấm được, vẫn giữ màu `primary` (đặc tả §3.1: trạng thái đang xử lý).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
    @DrawableRes icon: Int? = null,
    /** Kiểu khoá nhạt của Register.dc.html: nền `line`, chữ `inkMuted` (mặc định: nền `disabledFill`, chữ trắng). */
    mutedWhenDisabled: Boolean = false,
) {
    val colors = GgTheme.colors
    val muted = !enabled && mutedWhenDisabled
    BaseButton(
        text = text, onClick = onClick, modifier = modifier, enabled = enabled && !loading, size = size, icon = icon,
        background = when { enabled -> colors.primary; muted -> colors.line; else -> colors.disabledFill },
        content = if (muted) colors.inkMuted else colors.onPrimary,
        border = null,
        loading = loading,
    )
}

/** Nút phụ: nền `surfaceRaised`, viền 1 dp `lineStrong`, chữ `ink`. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Large,
    @DrawableRes icon: Int? = null,
    loading: Boolean = false,
) {
    val colors = GgTheme.colors
    BaseButton(
        text = text, onClick = onClick, modifier = modifier, enabled = enabled && !loading, size = size, icon = icon,
        background = colors.surfaceRaised,
        content = if (enabled) colors.ink else colors.inkMuted,
        border = colors.lineStrong,
        loading = loading,
    )
}

/**
 * Nút viền đứt "Thêm khung sạc hoặc xả" (TouStrategy.dc.html): cao 48 dp, viền 1 dp đứt màu `primary`,
 * bo 12 dp, nền `surfaceRaised`, chữ 14/600 `primary`, icon 20 + cách 8 dp.
 * [neutral]: "Đính kèm ảnh hoặc video" (Support.dc.html) — khung cao 44 dp trong vùng chạm 48, bo 10 dp, không nền,
 * viền đứt `lineStrong`, chữ 13/600 `ink`, icon 18.
 */
@Composable
fun DashedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = R.drawable.ic_plus,
    neutral: Boolean = false,
) {
    val colors = GgTheme.colors
    val radius = if (neutral) GgRadius.radiusMd else 12.dp
    val tint = when {
        !enabled -> colors.inkMuted
        neutral -> colors.ink
        else -> colors.primary
    }
    val borderColor = if (neutral) colors.lineStrong else tint
    // Kiểu neutral: khung vẽ cao 44 nằm giữa vùng chạm 48 (hụt 2 dp mỗi phía).
    val inset = if (neutral) 2.dp else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(radius))
            .then(if (neutral) Modifier else Modifier.background(colors.surfaceRaised))
            .drawBehind {
                val stroke = 1.dp.toPx()
                val dy = inset.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, dy + stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - 2 * dy - stroke),
                    cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(if (neutral) 18.dp else 20.dp))
        Text(
            text,
            style = GgTheme.typography.label.copy(fontSize = if (neutral) 13.sp else 14.sp, lineHeight = if (neutral) 16.sp else 20.sp),
            color = tint,
        )
    }
}

/**
 * Nút chữ đỏ "Xoá chiến lược này" / "Xoá khung này" (TouStrategy.dc.html): không nền, chữ 14/600 `dangerText`,
 * icon thùng rác. Cao 48 dp (prototype 44 dp, nâng lên vùng chạm tối thiểu).
 */
@Composable
fun DangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_trash,
) {
    val color = GgTheme.colors.dangerText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(GgRadius.radiusMd))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp), color = color)
    }
}

/**
 * Nút đỏ nền đặc "Xoá tài khoản" (Account.dc.html, hộp xoá): nền `danger`, chữ trắng 15/600, bo 10.
 * Bị khoá: kiểu khoá nhạt (nền `line`, chữ `inkMuted`). [loading]: vòng xoay thay chữ, giữ nền `danger`.
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
) {
    val colors = GgTheme.colors
    val active = enabled || loading
    BaseButton(
        text = text, onClick = onClick, modifier = modifier, enabled = enabled && !loading, size = size, icon = null,
        background = if (active) colors.danger else colors.line,
        content = if (active) colors.onPrimary else colors.inkMuted,
        border = null,
        loading = loading,
    )
}

/**
 * Nút viền đỏ "Đăng xuất" (Me.dc.html): cao 48, bo 10, viền 1 dp `danger`, không nền; icon 20 + chữ 14/600 `danger`.
 */
@Composable
fun DangerOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val color = GgTheme.colors.danger
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, color, shape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = GgTheme.typography.label.copy(fontSize = 14.sp, lineHeight = 20.sp), color = color)
    }
}

@Composable
private fun BaseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    size: ButtonSize,
    @DrawableRes icon: Int?,
    background: Color,
    content: Color,
    border: Color?,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Row(
        modifier = modifier
            .height(size.height)
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (loading) Modifier.semantics { stateDescription = text } else Modifier)
            .padding(horizontal = GgSpacing.space4),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(text, style = buttonText.copy(fontWeight = FontWeight.SemiBold), color = content, maxLines = 1)
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ButtonsPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            PrimaryButton("Đăng nhập", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Đăng nhập", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Thêm thiết bị bằng mã QR", onClick = {}, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Huỷ", onClick = {}, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
                PrimaryButton("Lưu", onClick = {}, size = ButtonSize.Medium, enabled = false, modifier = Modifier.weight(2f))
            }
            DashedButton("Thêm khung sạc hoặc xả", onClick = {})
            DangerTextButton("Xoá chiến lược này", onClick = {})
        }
    }
}
