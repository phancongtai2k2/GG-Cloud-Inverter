package vn.ggpower.inverter.ui.forgot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/** Thời gian chờ trước khi tự về Đăng nhập (đặc tả §3.12). */
const val DONE_REDIRECT_MS = 2_000L

/**
 * 12d: sau [DONE_REDIRECT_MS] tự về Đăng nhập; nút "Về Đăng nhập", nút ← và nút back của hệ thống về ngay. Chỉ chuyển
 * một lần dù nhiều đường cùng kích hoạt.
 */
@Composable
fun ForgotDoneRoute(onToLogin: () -> Unit) {
    val go by rememberUpdatedState(onToLogin)
    var left by remember { mutableStateOf(false) }
    val leave = { if (!left) { left = true; go() } }
    LaunchedEffect(Unit) {
        delay(DONE_REDIRECT_MS)
        leave()
    }
    BackHandler { leave() }
    ForgotDoneScreen(onToLogin = leave)
}

/**
 * Màn 12d · Quên mật khẩu – Thành công (ForgotPasswordDone.png; đặc tả §3.12). Thanh bước tô đủ 3 vạch; khối giữa
 * cách đỉnh 48: vòng tròn 72 dp `primarySoft` có dấu tích 36 dp `primary`, tiêu đề 24/32 700, câu 14/22 `inkMuted`,
 * nút "Về Đăng nhập" kéo hết bề rộng; các khối cách 16, canh giữa.
 */
@Composable
fun ForgotDoneScreen(onToLogin: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.forgot_title), onBack = onToLogin)
        Column(
            Modifier.padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2),
            verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
        ) {
            StepProgress(forgotSteps(), current = 4)
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check_success), contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
                }
                Text(
                    stringResource(R.string.forgot_done_heading),
                    style = GgTheme.typography.body.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    stringResource(R.string.forgot_done_body),
                    style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(stringResource(R.string.forgot_done_to_login), onClick = onToLogin, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ForgotDonePreview() {
    GgPowerTheme(darkTheme = false) { ForgotDoneScreen({}) }
}
