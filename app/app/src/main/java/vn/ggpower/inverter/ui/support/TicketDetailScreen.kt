package vn.ggpower.inverter.ui.support

import android.content.ClipData
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.support.FakeSupportTicketRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.support.AttachmentLine
import vn.ggpower.inverter.domain.support.MessageAuthor
import vn.ggpower.inverter.domain.support.TicketDetail
import vn.ggpower.inverter.domain.support.TicketMessage
import vn.ggpower.inverter.domain.support.TicketStatus
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.domain.support.messageShowsDate
import vn.ggpower.inverter.ui.components.GgToast
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun TicketDetailRoute(onBack: () -> Unit, viewModel: TicketDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TicketDetailScreen(
        state = state,
        actions = TicketDetailActions(
            onBack = onBack,
            onCopy = {
                state.detail?.summary?.code?.let { code ->
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("ticket", code)))
                        viewModel.onCopied()
                    }
                }
            },
            onMessage = viewModel::onMessage,
            onSend = viewModel::onSend,
            onRetry = viewModel::retry,
        ),
    )
}

/**
 * Màn 8 · Chi tiết yêu cầu (TicketDetail.dc.html, đặc tả §3.8). Thẻ phiếu, các lượt trao đổi, ô nhập + nút Gửi ở đáy.
 * Thêm so với prototype (chốt 03/10/2026): nút sao chép cạnh mã; phiếu Đã đóng thay ô nhập bằng ghi chú;
 * tải: vòng xoay; lỗi tải: hộp lỗi + "Thử lại"; gửi lỗi: hộp lỗi trên ô nhập, giữ nguyên chữ.
 */
@Composable
fun TicketDetailScreen(state: TicketDetailUiState, actions: TicketDetailActions, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface).statusBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.screen_ticket_detail), onBack = actions.onBack)
        when (val load = state.load) {
            DetailLoad.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp), color = colors.primary, strokeWidth = 3.dp)
            }
            is DetailLoad.Error -> LoadError(load.noConnection, actions.onRetry)
            is DetailLoad.Content -> Content(load.detail, state, actions)
        }
    }
}

@Composable
private fun ColumnScope.Content(detail: TicketDetail, state: TicketDetailUiState, actions: TicketDetailActions) {
    val listState = rememberLazyListState()
    // Tin mới (của mình hoặc của bộ phận hỗ trợ) thì cuộn xuống cuối.
    LaunchedEffect(detail.messages.size) {
        if (detail.messages.isNotEmpty()) listState.animateScrollToItem(detail.messages.size)
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space3),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Thẻ cách lượt đầu 12 (prototype: main gap 12, giữa các lượt 10).
            item(key = "card") { TicketCard(detail, state.copied, actions.onCopy, Modifier.padding(bottom = 2.dp)) }
            items(detail.messages, key = { it.id }) { MessageItem(it, messageShowsDate(it.sentAt, detail.summary.createdAt)) }
        }
        // Gọi bản không phụ thuộc ColumnScope (đang ở trong Box của ColumnScope.Content).
        androidx.compose.animation.AnimatedVisibility(
            visible = state.copied,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = GgSpacing.space3),
        ) { CopiedToast() }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space3),
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space2),
    ) {
        if (detail.acceptsReplies) {
            state.sendFailed?.let { ErrorBox(stringResource(if (it.noConnection) R.string.error_no_internet else R.string.error_server)) }
            Composer(state, actions)
        } else {
            Text(
                stringResource(R.string.ticket_detail_closed_note),
                style = GgTheme.typography.bodySm,
                color = GgTheme.colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            )
        }
    }
}

/**
 * Thẻ phiếu (đệm 14 16, cách 6): mã đơn cách 12/16 `inkMuted` + nút sao chép + chip trạng thái không chấm; tiêu đề
 * 16/22 600; "Loại · Dự án · dd/MM/yyyy HH:mm" 12/16; nội dung 13/20 (cách thêm 4); dòng 📎 12 600 `primary`.
 */
@Composable
private fun TicketCard(detail: TicketDetail, copied: Boolean, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val ticket = detail.summary
    GgCard(modifier, contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = 14.dp), verticalSpacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            Text(ticket.code, style = GgTheme.typography.code.copy(fontSize = 12.sp, lineHeight = 16.sp), color = colors.inkMuted)
            // Nút nhỏ để không làm cao dòng; Compose tự nới vùng chạm lên 48 dp.
            Box(
                Modifier.size(20.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (copied) R.drawable.ic_check_thin else R.drawable.ic_copy),
                    contentDescription = stringResource(if (copied) R.string.support_sent_copied else R.string.cd_copy_ticket_code),
                    tint = if (copied) colors.primary else colors.inkMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            StatusChip(ticket.status.label(), ticket.status.tone, showDot = false)
        }
        Text(
            ticket.title,
            style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        val type = ticket.type.label()
        val time = GgFormat.dateTimeLog(ticket.createdAt)
        Text(
            if (ticket.projectName != null) stringResource(R.string.ticket_detail_meta, type, ticket.projectName, time)
            else stringResource(R.string.ticket_detail_meta_no_project, type, time),
            style = secondaryText,
            color = colors.inkMuted,
        )
        Text(detail.description, style = GgTheme.typography.bodySm, color = colors.ink, modifier = Modifier.padding(top = 4.dp))
        attachmentText(detail.attachmentLine)?.let { text ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_paperclip), contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                Text(text, style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.primary)
            }
        }
    }
}

@Composable
private fun attachmentText(line: AttachmentLine): String? = when (line) {
    is AttachmentLine.PhotosAndSystem -> stringResource(R.string.ticket_detail_attach_both, line.count)
    is AttachmentLine.PhotosOnly -> stringResource(R.string.ticket_detail_attach_photos, line.count)
    AttachmentLine.SystemOnly -> stringResource(R.string.ticket_detail_attach_system)
    AttachmentLine.None -> null
}

@Composable
private fun TicketType.label(): String = stringResource(
    when (this) {
        TicketType.Technical -> R.string.support_type_technical
        TicketType.Account -> R.string.support_type_account
        TicketType.Other -> R.string.support_type_other
    },
)

/**
 * Một lượt trao đổi (rộng tối đa 82 %, nhãn 12/16 `inkMuted` trên bong bóng 14/20 đệm 10 14):
 * hỗ trợ — trái, nền trắng viền `cardBorder`, bo 16 16 16 4; bạn — phải, nền `primary` chữ trắng, bo 16 16 4 16.
 */
@Composable
private fun MessageItem(message: TicketMessage, showDate: Boolean) {
    val colors = GgTheme.colors
    val mine = message.author == MessageAuthor.Me
    val time = if (showDate) GgFormat.dateTimeShort(message.sentAt) else GgFormat.time(message.sentAt.toLocalTime())
    val big = 16.dp
    val small = 4.dp
    val shape = if (mine) RoundedCornerShape(big, big, small, big) else RoundedCornerShape(big, big, big, small)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.fillMaxWidth(0.82f),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(if (mine) R.string.ticket_detail_author_me else R.string.ticket_detail_author_support, time),
                style = secondaryText,
                color = colors.inkMuted,
            )
            Text(
                message.text,
                style = bubbleText,
                color = if (mine) colors.onPrimary else colors.ink,
                modifier = Modifier
                    .clip(shape)
                    .background(if (mine) colors.primaryFill else colors.surfaceRaised)
                    .then(if (mine) Modifier else Modifier.border(1.dp, colors.cardBorder, shape))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** Ô "Nhập tin nhắn…" (cao 48, bo 10, viền `lineStrong`) + nút Gửi tròn 48 `primary`. */
@Composable
private fun Composer(state: TicketDetailUiState, actions: TicketDetailActions) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    val label = stringResource(R.string.ticket_detail_message_label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
        BasicTextField(
            value = state.message,
            onValueChange = actions.onMessage,
            textStyle = bubbleText.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.primary),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(shape)
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.lineStrong, shape)
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.message.isEmpty()) {
                        Text(stringResource(R.string.ticket_detail_message_hint), style = bubbleText, color = colors.inkMuted, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        // Giữ màu `primary` như prototype; ô trống thì nút không phản hồi.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.primaryFill)
                .clickable(enabled = state.canSend, role = Role.Button, onClick = actions.onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (state.sending) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.ticket_detail_send),
                    tint = colors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CopiedToast() = GgToast(stringResource(R.string.support_sent_copied))

@Composable
private fun ColumnScope.LoadError(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = GgSpacing.space5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4),
    ) {
        ErrorBox(stringResource(if (noConnection) R.string.error_no_internet else R.string.error_server))
        SecondaryButton(stringResource(R.string.action_retry), onClick = onRetry, size = ButtonSize.Medium)
    }
}

private val secondaryText @Composable get() = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal)
private val bubbleText @Composable get() = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp)

// ---------- Preview ----------

internal fun previewTicketDetailState(
    ticketId: String = "t1",
    status: TicketStatus? = null,
    message: String = "",
    sendFailed: SubmitFailure? = null,
    copied: Boolean = false,
): TicketDetailUiState {
    val base = FakeSupportTicketRepository.SAMPLE_DETAILS.first { it.summary.id == ticketId }
    val detail = if (status != null) base.copy(summary = base.summary.copy(status = status)) else base
    return TicketDetailUiState(load = DetailLoad.Content(detail), message = message, sendFailed = sendFailed, copied = copied)
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun TicketDetailPreview() {
    GgPowerTheme(darkTheme = false) { TicketDetailScreen(previewTicketDetailState(), TicketDetailActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun TicketDetailClosedPreview() {
    GgPowerTheme(darkTheme = false) { TicketDetailScreen(previewTicketDetailState("t4"), TicketDetailActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun TicketDetailSendFailedPreview() {
    GgPowerTheme(darkTheme = false) {
        TicketDetailScreen(
            previewTicketDetailState(message = "Kỹ thuật gọi lúc mấy giờ ạ?", sendFailed = SubmitFailure(noConnection = true)),
            TicketDetailActions(),
        )
    }
}
