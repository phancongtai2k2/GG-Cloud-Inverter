package vn.ggpower.inverter.ui.support

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.data.support.FakeSupportTicketRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.support.MAX_TICKET_ATTACHMENTS
import vn.ggpower.inverter.domain.support.TicketAttachment
import vn.ggpower.inverter.domain.support.TicketDraft
import vn.ggpower.inverter.domain.support.TicketFieldError
import vn.ggpower.inverter.domain.support.TicketStatus
import vn.ggpower.inverter.domain.support.TicketSummary
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.DashedButton
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.projects.ProjectStatusChip
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.LocalDateTime

/** Các thao tác của màn 8, gom lại cho gọn chữ ký và Preview. */
data class SupportActions(
    val onBack: () -> Unit = {},
    val onTab: (SupportTab) -> Unit = {},
    val onType: (TicketType) -> Unit = {},
    val onToggleProjectMenu: () -> Unit = {},
    val onDismissProjectMenu: () -> Unit = {},
    val onPickProject: (String) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onAttach: () -> Unit = {},
    val onRemoveAttachment: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onRetryLoad: () -> Unit = {},
    val onRetryTickets: () -> Unit = {},
    val onOpenTicket: (String) -> Unit = {},
)

/**
 * Màn 8 · Yêu cầu hỗ trợ: nối ViewModel với điều hướng. Đính kèm dùng bộ chọn ảnh / video của hệ thống
 * (chốt 03/10/2026); gửi thành công thì gọi [onSent] để mở màn 24.
 */
@Composable
fun SupportRoute(
    onBack: () -> Unit,
    onOpenTicket: (String) -> Unit,
    onSent: (TicketSummary) -> Unit,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_TICKET_ATTACHMENTS)) { uris ->
        if (uris.isNotEmpty()) viewModel.onAttachments(uris.map { context.toAttachment(it) })
    }
    LaunchedEffect(state.sent) {
        state.sent?.let {
            onSent(it)
            viewModel.onSentHandled()
        }
    }
    SupportScreen(
        state = state,
        actions = SupportActions(
            onBack = onBack,
            onTab = viewModel::onTab,
            onType = viewModel::onType,
            onToggleProjectMenu = viewModel::onToggleProjectMenu,
            onDismissProjectMenu = viewModel::onDismissProjectMenu,
            onPickProject = viewModel::onPickProject,
            onTitle = viewModel::onTitle,
            onDescription = viewModel::onDescription,
            onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onRemoveAttachment = viewModel::onRemoveAttachment,
            onSubmit = viewModel::onSubmit,
            onRetryLoad = viewModel::retryLoad,
            onRetryTickets = viewModel::retryTickets,
            onOpenTicket = onOpenTicket,
        ),
    )
}

/** Tên tệp và loại (ảnh / video) của một Uri từ bộ chọn hệ thống. */
private fun Context.toAttachment(uri: Uri): TicketAttachment {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
    val isVideo = contentResolver.getType(uri)?.startsWith("video/") == true
    return TicketAttachment(uri.toString(), name, isVideo)
}

/**
 * Màn 8 · Yêu cầu hỗ trợ (Support.png, Support__from-alert.png, Support__my-requests.png; html/Support.dc.html;
 * đặc tả §3.8; Thay đổi 0.6 §6). Bộ chọn hai thẻ cố định dưới tiêu đề; các khối cách 14, lề 20.
 */
@Composable
fun SupportScreen(state: SupportUiState, actions: SupportActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(GgTheme.colors.surface).statusBarsPadding()) {
        ScreenHeader(title = stringResource(R.string.screen_support), onBack = actions.onBack)
        SegmentedControl(
            options = listOf(stringResource(R.string.support_tab_new), stringResource(R.string.support_tab_mine)),
            selectedIndex = state.tab.ordinal,
            onSelect = { actions.onTab(SupportTab.entries[it]) },
            groupLabel = stringResource(R.string.screen_support),
            modifier = Modifier.padding(horizontal = GgSpacing.space5),
        )
        when (state.tab) {
            SupportTab.New -> when (val load = state.load) {
                FormLoad.Loading -> LoadingBlocks()
                is FormLoad.Error -> ErrorBlock(load.noConnection, actions.onRetryLoad)
                FormLoad.Ready -> NewTicketForm(state, actions)
            }
            SupportTab.Mine -> when (val tickets = state.tickets) {
                TicketsState.Loading -> LoadingBlocks()
                is TicketsState.Error -> ErrorBlock(tickets.noConnection, actions.onRetryTickets)
                is TicketsState.Content -> TicketList(tickets.tickets, actions.onOpenTicket)
            }
        }
    }
}

// ---------- Thẻ Gửi yêu cầu mới ----------

private val labelStyle
    @Composable get() = GgTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

private val secondaryStyle
    @Composable get() = GgTheme.typography.caption.copy(fontWeight = FontWeight.Normal)

@Composable
private fun ColumnScope.NewTicketForm(state: SupportUiState, actions: SupportActions) {
    val errors = state.errors
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.alert?.let { AlertStrip(it) }
        LabeledBlock(stringResource(R.string.support_type_label)) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.support_type_technical),
                    stringResource(R.string.support_type_account),
                    stringResource(R.string.support_type_other),
                ),
                selectedIndex = state.draft.type.ordinal,
                onSelect = { actions.onType(TicketType.entries[it]) },
                groupLabel = stringResource(R.string.support_type_label),
            )
        }
        if (state.showsProject) {
            val error = if (TicketFieldError.ProjectMissing in errors) stringResource(R.string.support_error_project) else null
            LabeledBlock(stringResource(R.string.support_project_label)) {
                ProjectDropdown(
                    label = stringResource(R.string.support_project_label),
                    projects = state.projects,
                    selected = state.selectedProject,
                    open = state.projectMenuOpen,
                    error = error,
                    onToggle = actions.onToggleProjectMenu,
                    onDismiss = actions.onDismissProjectMenu,
                    onPick = actions.onPickProject,
                )
                if (error != null) Text(error, style = GgTheme.typography.caption.copy(lineHeight = 16.sp), color = GgTheme.colors.dangerText)
            }
        }
        GgTextField(
            label = stringResource(R.string.support_title_label),
            value = state.draft.title,
            onValueChange = actions.onTitle,
            size = FieldSize.Ticket,
            placeholder = stringResource(R.string.support_title_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            errorText = if (TicketFieldError.TitleEmpty in errors) stringResource(R.string.support_error_title) else null,
        )
        GgTextField(
            label = stringResource(R.string.support_desc_label),
            value = state.draft.description,
            onValueChange = actions.onDescription,
            size = FieldSize.Ticket,
            placeholder = stringResource(R.string.support_desc_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            errorText = if (TicketFieldError.DescriptionEmpty in errors) stringResource(R.string.support_error_desc) else null,
            multilineHeight = 96.dp,
        )
        DashedButton(
            text = stringResource(R.string.support_attach),
            onClick = actions.onAttach,
            icon = R.drawable.ic_paperclip,
            neutral = true,
            enabled = state.draft.attachmentSlotsLeft > 0,
        )
        state.draft.attachments.forEach { AttachmentRow(it, actions.onRemoveAttachment) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = GgSpacing.space4),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.submitFailed?.let {
            ErrorBox(stringResource(if (it.noConnection) R.string.error_no_internet else R.string.error_server))
        }
        PrimaryButton(
            text = stringResource(if (state.submitFailed != null) R.string.support_resend else R.string.support_submit),
            onClick = actions.onSubmit,
            loading = state.submitting,
            size = ButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Nhãn 13/16 600 + nội dung, cách 6. */
@Composable
private fun LabeledBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = labelStyle, color = GgTheme.colors.ink)
        content()
    }
}

/**
 * Dải "Tạo từ cảnh báo" (Support__from-alert.png): nền `dangerSoft` bo 12, đệm 10 12, vạch đỏ 4 dp bên trái cách 10;
 * nhãn 12/16 600 đỏ, "‹tiêu đề› · HH:mm dd/MM/yyyy" 13/18 600, ghi chú 12/16 `inkMuted`.
 */
@Composable
private fun AlertStrip(alert: AlertBanner) {
    val colors = GgTheme.colors
    val time = "${GgFormat.time(alert.startedAt.toLocalTime())} ${GgFormat.date(alert.startedAt.toLocalDate())}"
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.dangerSoft)
            .padding(horizontal = GgSpacing.space3, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(GgRadius.radiusPill)).background(colors.danger))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.support_from_alert), style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.danger)
            Text(
                stringResource(R.string.support_from_alert_title, alert.title, time),
                style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(stringResource(R.string.support_from_alert_note), style = secondaryStyle, color = colors.inkMuted)
        }
    }
}

/** "Địa chỉ · cấu hình" dưới tên dự án. */
private val ProjectSummary.meta: String get() = "$address · $configuration"

/**
 * Danh sách thả xuống "Dự án cần hỗ trợ" (đặc tả §3.8): ô đóng cao tối thiểu 56, đệm 8 12 8 14, viền 1 `lineStrong`
 * (mở: 2 `primary`; lỗi: `dangerText`); tên 14/20 600, "địa chỉ · cấu hình" 12/16 một dòng, chip nhỏ, mũi tên 20 xoay khi
 * mở. Danh sách nổi cách 6 dp: nền trắng bo 12 viền `cardBorder`, đệm 6; dòng đệm 10 bo 8, dòng đang chọn nền
 * `primarySoft` và dấu ✓.
 */
@Composable
private fun ProjectDropdown(
    label: String,
    projects: List<ProjectSummary>,
    selected: ProjectSummary?,
    open: Boolean,
    error: String?,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = GgTheme.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    var width by remember { mutableIntStateOf(0) }
    var height by remember { mutableIntStateOf(0) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onSizeChanged { width = it.width; height = it.height }
                .clip(shape)
                .background(colors.surfaceRaised)
                .border(
                    if (open) 2.dp else 1.dp,
                    when {
                        open -> colors.primary
                        error != null -> colors.dangerText
                        else -> colors.lineStrong
                    },
                    shape,
                )
                .clickable(role = Role.DropdownList, onClick = onToggle)
                .semantics {
                    stateDescription = selected?.name.orEmpty()
                    if (error != null) error(error)
                }
                .padding(start = 14.dp, end = GgSpacing.space3, top = GgSpacing.space2, bottom = GgSpacing.space2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null) {
                ProjectText(selected, Modifier.weight(1f))
                ProjectStatusChip(selected.status, compact = true)
            } else {
                Text(
                    stringResource(R.string.support_project_placeholder),
                    style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f),
            )
        }
        if (open) {
            val gap = with(density) { 6.dp.roundToPx() }
            Popup(
                offset = IntOffset(0, height + gap),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                val menuShape = RoundedCornerShape(12.dp)
                Column(
                    Modifier
                        .width(with(density) { width.toDp() })
                        .shadow(8.dp, menuShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                        .clip(menuShape)
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.cardBorder, menuShape)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    projects.forEach { p ->
                        val isSelected = p.id == selected?.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(GgSpacing.space2))
                                .background(if (isSelected) colors.primarySoft else Color.Transparent)
                                .clickable(role = Role.RadioButton) { onPick(p.id) }
                                .semantics { this.selected = isSelected }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProjectText(p, Modifier.weight(1f))
                            ProjectStatusChip(p.status, compact = true)
                            Box(Modifier.size(20.dp)) {
                                if (isSelected) {
                                    Icon(
                                        painterResource(R.drawable.ic_check_thin),
                                        contentDescription = stringResource(R.string.cd_support_project_selected),
                                        tint = colors.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectText(project: ProjectSummary, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            project.name,
            style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            color = GgTheme.colors.ink,
        )
        Text(project.meta, style = secondaryStyle, color = GgTheme.colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Tệp đã chọn (chưa có thiết kế): dòng cao 48 bo 10 viền `line`, icon ảnh / video 18, tên một dòng, nút ✕ 48. */
@Composable
private fun AttachmentRow(file: TicketAttachment, onRemove: (String) -> Unit) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.line, shape)
            .padding(start = GgSpacing.space3),
        horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(if (file.isVideo) R.drawable.ic_play else R.drawable.ic_image),
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            file.name,
            style = GgTheme.typography.bodySm,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onRemove(file.uri) }, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.cd_support_remove_attachment, file.name),
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---------- Thẻ Yêu cầu của tôi ----------

/** Dòng ghi chú 12/16 `inkMuted`, rồi các thẻ phiếu cách 10 (Support__my-requests.png). */
@Composable
private fun TicketList(tickets: List<TicketSummary>, onOpen: (String) -> Unit) {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GgSpacing.space5, end = GgSpacing.space5, top = 14.dp, bottom = GgSpacing.space4 + navBar),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "caption") {
            Text(stringResource(R.string.support_mine_caption), style = secondaryStyle, color = GgTheme.colors.inkMuted)
        }
        if (tickets.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.support_mine_empty),
                    style = GgTheme.typography.bodySm,
                    color = GgTheme.colors.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = GgSpacing.space6),
                )
            }
        }
        items(tickets, key = { it.id }) { TicketCard(it) { onOpen(it.id) } }
    }
}

@Composable
internal fun TicketStatus.label(): String = stringResource(
    when (this) {
        TicketStatus.Received -> R.string.support_status_received
        TicketStatus.InProgress -> R.string.support_status_in_progress
        TicketStatus.Resolved -> R.string.support_status_resolved
        TicketStatus.Closed -> R.string.support_status_closed
    },
)

internal val TicketStatus.tone: StatusTone
    get() = when (this) {
        TicketStatus.Received -> StatusTone.Info
        TicketStatus.InProgress -> StatusTone.Warning
        TicketStatus.Resolved -> StatusTone.Success
        TicketStatus.Closed -> StatusTone.Muted
    }

/** Phiếu không gắn dự án hiện tên loại (prototype: "Tài khoản"). */
@Composable
internal fun TicketSummary.projectLabel(): String = projectName ?: stringResource(
    when (type) {
        TicketType.Technical -> R.string.support_type_technical
        TicketType.Account -> R.string.support_type_account
        TicketType.Other -> R.string.support_type_other
    },
)

/**
 * Thẻ phiếu (đệm 14 16, cách 6): mã đơn cách 12/16 `inkMuted` + chip trạng thái không chấm; tiêu đề 15/22 600;
 * icon nhà 14 + "dự án · ngày gửi" 12/16.
 */
@Composable
private fun TicketCard(ticket: TicketSummary, onClick: () -> Unit) {
    val colors = GgTheme.colors
    GgCard(onClick = onClick, contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = 14.dp), verticalSpacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            Text(
                ticket.code,
                style = GgTheme.typography.code.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            StatusChip(ticket.status.label(), ticket.status.tone, showDot = false)
        }
        Text(
            ticket.title,
            style = GgTheme.typography.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_site_home), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
            Text(
                stringResource(R.string.support_ticket_meta, ticket.projectLabel(), GgFormat.date(ticket.createdAt.toLocalDate())),
                style = secondaryStyle,
                color = colors.inkMuted,
            )
        }
    }
}

// ---------- Tải / lỗi ----------

@Composable
private fun LoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(
        Modifier.padding(start = GgSpacing.space5, end = GgSpacing.space5, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
    }
}

@Composable
private fun ColumnScope.ErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = GgSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (noConnection) R.string.error_no_internet else R.string.error_server),
            style = GgTheme.typography.body,
            color = GgTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.action_retry), onClick = onRetry, size = ButtonSize.Medium)
    }
}

// ---------- Preview ----------

internal fun previewSupportState(
    tab: SupportTab = SupportTab.New,
    fromAlert: Boolean = false,
    projectMenuOpen: Boolean = false,
    showErrors: Boolean = false,
    projectId: String? = "p1",
): SupportUiState = SupportUiState(
    tab = tab,
    load = FormLoad.Ready,
    projects = FakeProjectRepository.SAMPLE,
    alert = if (fromAlert) AlertBanner("Mất kết nối lưới điện", LocalDateTime.of(2026, 9, 24, 7, 12)) else null,
    draft = if (fromAlert) {
        TicketDraft(
            projectId = "p1",
            title = "Mất kết nối lưới điện – Nhà của bạn",
            description = "Cảnh báo Lỗi lúc 07:12 · 24/09/2026, vẫn đang diễn ra. Mã lỗi: 0x200 CurrErrReg · bit [MÃ BIT].",
        )
    } else {
        TicketDraft(projectId = projectId)
    },
    projectMenuOpen = projectMenuOpen,
    showErrors = showErrors,
    tickets = TicketsState.Content(FakeSupportTicketRepository.SAMPLE),
)

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SupportPreview() {
    GgPowerTheme(darkTheme = false) { SupportScreen(previewSupportState(), SupportActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SupportFromAlertPreview() {
    GgPowerTheme(darkTheme = false) { SupportScreen(previewSupportState(fromAlert = true), SupportActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SupportErrorsPreview() {
    GgPowerTheme(darkTheme = false) { SupportScreen(previewSupportState(showErrors = true, projectId = null), SupportActions()) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SupportMinePreview() {
    GgPowerTheme(darkTheme = false) { SupportScreen(previewSupportState(tab = SupportTab.Mine), SupportActions()) }
}
