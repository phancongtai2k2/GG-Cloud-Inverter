package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Chèn [arg] vào chỗ `%1$s` của [template] và in đậm màu [color] (prototype: `<strong style="color: ink">`).
 * [template] là chuỗi chưa định dạng (`stringResource(id)` không truyền tham số).
 */
fun boldArgument(template: String, arg: String, color: Color): AnnotatedString = buildAnnotatedString {
    val parts = template.split("%1\$s", limit = 2)
    append(parts[0])
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(arg) }
    if (parts.size > 1) append(parts[1])
}

/**
 * Thân bước nhập mã (ForgotPasswordOtp.dc.html, RegisterOtp.dc.html), các khối cách 16: tiêu đề 24/32 700; mô tả 14/22
 * `inkMuted` có nơi nhận mã in đậm; [OtpInput]; hàng "Gửi lại mã sau 00:45" (13/20, giờ đậm số đều) và liên kết
 * "Gửi lại mã" 13/20 600 (`dayChipOffText` khi còn đếm, `primary` khi được gửi); nút chính 52 dp, bật khi đủ 6 số.
 * Hàng gửi lại cao 48 dp để đủ vùng chạm, khoảng cách hai bên bù lại để nhìn vẫn là 16.
 */
@Composable
fun OtpForm(
    heading: String,
    body: AnnotatedString,
    code: String,
    onCodeChange: (String) -> Unit,
    errorText: String?,
    resendIn: Int,
    resending: Boolean,
    onResend: () -> Unit,
    confirmLabel: String,
    verifying: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val canResend = resendIn == 0 && !resending
    Column(modifier) {
        Text(
            heading,
            style = GgTheme.typography.body.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(GgSpacing.space4))
        Text(body, style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp), color = colors.inkMuted)
        Spacer(Modifier.height(GgSpacing.space4))
        OtpInput(code, onCodeChange, isError = errorText != null, errorText = errorText, onDone = onConfirm)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (resendIn > 0) {
                    Text(
                        boldArgument(stringResource(R.string.otp_resend_in), GgFormat.countdown(resendIn), colors.ink),
                        style = GgTheme.typography.bodySm.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkMuted,
                    )
                }
            }
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(enabled = canResend, role = Role.Button, onClick = onResend),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(R.string.otp_resend),
                    style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                    color = if (canResend) colors.primary else colors.dayChipOffText,
                )
            }
        }
        PrimaryButton(
            text = confirmLabel,
            onClick = onConfirm,
            enabled = code.length == OTP_LENGTH,
            loading = verifying,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun OtpFormPreview() {
    GgPowerTheme(darkTheme = false) {
        OtpForm(
            heading = "Nhập mã xác thực",
            body = boldArgument("Mã 6 số đã gửi tới %1\$s. Mã có hiệu lực trong 5 phút.", "0912 ••• 678", GgTheme.colors.ink),
            code = "482913", onCodeChange = {}, errorText = null, resendIn = 45, resending = false, onResend = {},
            confirmLabel = "Xác nhận", verifying = false, onConfirm = {},
            modifier = Modifier.padding(20.dp),
        )
    }
}

