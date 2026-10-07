package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.auth.PasswordStrength
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Ô mật khẩu cỡ [FieldSize.Form] có nút con mắt 48 dp ở mép phải (icon 20 dp `inkMuted`) để hiện / ẩn
 * (ForgotPasswordNew.dc.html, Register.dc.html). Trạng thái hiện / ẩn giữ riêng từng ô, còn khi xoay màn hình.
 */
@Composable
fun PasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    newPassword: Boolean = true,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    GgTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        size = FieldSize.Form,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        errorText = errorText,
        modifier = modifier.semantics { contentType = if (newPassword) ContentType.NewPassword else ContentType.Password },
        trailing = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_eye_off else R.drawable.ic_eye),
                    contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                    tint = GgTheme.colors.inkMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

/**
 * Thanh độ mạnh mật khẩu (ForgotPasswordNew.dc.html): 4 vạch cao 4 dp bo tròn cách 4; dòng "Độ mạnh: Tốt" 12/16 600
 * cách 6. Màu theo mức (BO_SUNG §19): Yếu `dangerText`, Trung bình `warning`, Tốt / Mạnh `primary`; vạch chưa tô
 * `line`. [strength] null (ô trống): 4 vạch xám, không có chữ.
 */
@Composable
fun PasswordStrengthMeter(strength: PasswordStrength?, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val color = when (strength) {
        PasswordStrength.Weak -> colors.dangerText
        PasswordStrength.Fair -> colors.warning
        PasswordStrength.Good, PasswordStrength.Strong -> colors.primary
        null -> colors.line
    }
    val label = strength?.let {
        stringResource(
            R.string.password_strength,
            stringResource(
                when (it) {
                    PasswordStrength.Weak -> R.string.password_strength_weak
                    PasswordStrength.Fair -> R.string.password_strength_fair
                    PasswordStrength.Good -> R.string.password_strength_good
                    PasswordStrength.Strong -> R.string.password_strength_strong
                },
            ),
        )
    }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics { if (label != null) contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (strength != null && i < strength.bars) color else colors.line),
                )
            }
        }
        if (label != null) {
            Text(label, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun PasswordFieldPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PasswordField("Mật khẩu mới", "GGpower2026", {})
            PasswordStrengthMeter(PasswordStrength.Good)
            PasswordStrengthMeter(PasswordStrength.Weak)
            PasswordStrengthMeter(null)
        }
    }
}
