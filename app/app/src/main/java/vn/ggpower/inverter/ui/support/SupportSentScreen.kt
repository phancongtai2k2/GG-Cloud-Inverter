package vn.ggpower.inverter.ui.support

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun SupportSentRoute(
    onBack: () -> Unit,
    onViewMine: () -> Unit,
    onBackToService: () -> Unit,
    viewModel: SupportSentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SupportSentScreen(
        state = state,
        onBack = onBack,
        onCopy = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("ticket", state.code)))
                viewModel.onCopied()
            }
        },
        onViewMine = onViewMine,
        onBackToService = onBackToService,
    )
}

/**
 * Màn 24 · Gửi yêu cầu thành công (đặc tả §3.24). **Chưa có ảnh / HTML** — dựng từ component sẵn có theo chữ đặc tả
 * (chốt 03/10/2026): dấu kiểm lớn, "Đã gửi yêu cầu", thẻ mã yêu cầu có nút sao chép (như màn 6), thời gian phản hồi
 * dự kiến (chỉ loại Kỹ thuật), nút chính "Xem yêu cầu của tôi" và nút phụ "Về Dịch vụ" ở đáy.
 */
@Composable
fun SupportSentScreen(
    state: SupportSentUiState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onViewMine: () -> Unit,
    onBackToService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface).statusBarsPadding()) {
        ScreenHeader(title = "", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GgSpacing.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(GgSpacing.space6))
            Box(Modifier.size(72.dp).clip(CircleShape).background(colors.primarySoft), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check_thin), contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(GgSpacing.space4))
            Text(
                stringResource(R.string.support_sent_title),
                style = GgTheme.typography.screenTitle,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(GgSpacing.space6))
            CodeCard(state, onCopy)
            if (state.technical) {
                Spacer(Modifier.height(GgSpacing.space3))
                Text(
                    stringResource(R.string.support_sent_response_technical),
                    style = GgTheme.typography.bodySm,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(GgSpacing.space3))
            AnimatedVisibility(visible = state.copied, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(GgRadius.radiusPill))
                        .background(colors.ink)
                        .padding(horizontal = GgSpacing.space3, vertical = 6.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check_thin), contentDescription = null, tint = colors.surfaceRaised, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.support_sent_copied),
                        style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.surfaceRaised,
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(stringResource(R.string.support_sent_view_mine), onClick = onViewMine, modifier = Modifier.fillMaxWidth())
            SecondaryButton(stringResource(R.string.support_sent_back_service), onClick = onBackToService, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Thẻ mã yêu cầu: nhãn 12/16 `inkMuted`, mã đơn cách 15/22 600; nút sao chép tròn 48 (như dòng liên hệ màn 6). */
@Composable
private fun CodeCard(state: SupportSentUiState, onCopy: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = GgSpacing.space4, end = 6.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.support_sent_code_label), style = GgTheme.typography.caption, color = colors.inkMuted)
                Text(
                    state.code,
                    style = GgTheme.typography.code.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (state.copied) colors.primarySoft else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (state.copied) R.drawable.ic_check_thin else R.drawable.ic_copy),
                        contentDescription = stringResource(if (state.copied) R.string.support_sent_copied else R.string.cd_copy_ticket_code),
                        tint = if (state.copied) colors.primary else colors.inkMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SupportSentPreview() {
    GgPowerTheme(darkTheme = false) {
        SupportSentScreen(SupportSentUiState("#HT-0310-0151", technical = true), {}, {}, {}, {})
    }
}
