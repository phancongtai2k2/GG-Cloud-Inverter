package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgTheme

/** Số chữ số của mã xác thực (đặc tả §3.12). */
const val OTP_LENGTH = 6

/**
 * Ô nhập mã 6 số (ForgotPasswordOtp.dc.html, RegisterOtp.dc.html): 6 ô 48 × 56 dp dàn đều hai mép, bo 10, nền
 * `surfaceRaised`, chữ 22 700. Ô đã có số và ô đang nhập viền 2 dp `primary`; ô trống viền 1 dp `lineStrong`; lỗi
 * viền 2 dp `dangerText` (BO_SUNG §18). Thực chất là một ô nhập ẩn, nên dán mã và tự điền mã SMS đều được.
 */
@Composable
fun OtpInput(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    val colors = GgTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val description = stringResource(R.string.cd_otp_input, OTP_LENGTH)
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Column(modifier) {
        BasicTextField(
            value = TextFieldValue(code, TextRange(code.length)),
            onValueChange = { next ->
                val digits = next.text.filter { it.isDigit() }.take(OTP_LENGTH)
                if (digits != code) onCodeChange(digits)
            },
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(colors.primary.copy(alpha = 0f)),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = description
                    contentType = ContentType.SmsOtpCode
                    if (errorText != null) error(errorText)
                },
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(OTP_LENGTH) { i ->
                        val digit = code.getOrNull(i)
                        val active = focused && i == code.length.coerceAtMost(OTP_LENGTH - 1)
                        val (width, color) = when {
                            isError -> 2.dp to colors.dangerText
                            digit != null || active -> 2.dp to colors.primary
                            else -> 1.dp to colors.lineStrong
                        }
                        Box(
                            Modifier
                                .size(48.dp, 56.dp)
                                .clip(shape)
                                .background(colors.surfaceRaised)
                                .border(width, color, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                digit?.toString().orEmpty(),
                                style = GgTheme.typography.body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                                color = colors.ink,
                            )
                        }
                    }
                }
            },
        )
        if (errorText != null) {
            ErrorBox(errorText, Modifier.padding(top = 12.dp))
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun OtpInputPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OtpInput("482913", {})
            OtpInput("48", {})
            OtpInput("482910", {}, isError = true, errorText = "Mã không đúng. Còn 4 lần thử.")
        }
    }
}
