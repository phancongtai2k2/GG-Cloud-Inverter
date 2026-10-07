package vn.ggpower.inverter.ui.support

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.alert.AlertLoadException
import vn.ggpower.inverter.data.alert.AlertRepository
import vn.ggpower.inverter.data.project.ProjectLoadException
import vn.ggpower.inverter.data.project.ProjectRepository
import vn.ggpower.inverter.data.support.SupportTicketException
import vn.ggpower.inverter.data.support.SupportTicketRepository
import vn.ggpower.inverter.domain.alert.AlertDetail
import vn.ggpower.inverter.domain.alert.AlertSeverity
import vn.ggpower.inverter.domain.support.TicketAttachment
import vn.ggpower.inverter.domain.support.TicketDraft
import vn.ggpower.inverter.domain.support.TicketType
import vn.ggpower.inverter.domain.support.alertTicketDescription
import vn.ggpower.inverter.domain.support.alertTicketTitle
import vn.ggpower.inverter.navigation.Support
import javax.inject.Inject

@HiltViewModel
class SupportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    projectRepository: ProjectRepository,
    alertRepository: AlertRepository,
    ticketRepository: SupportTicketRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<Support>()
    private val controller = SupportController(
        projectId = route.projectId,
        alertId = route.alertId,
        projectRepository = projectRepository,
        alertRepository = alertRepository,
        ticketRepository = ticketRepository,
        prefillDescription = { alert ->
            val severity = context.getString(
                when (alert.severity) {
                    AlertSeverity.Error -> R.string.alert_severity_error
                    AlertSeverity.Warning -> R.string.alert_severity_warning
                    AlertSeverity.Info -> R.string.alert_severity_info
                },
            )
            alertTicketDescription(
                alert, severity,
                context.getString(R.string.support_prefill_desc_ongoing),
                context.getString(R.string.support_prefill_desc_ended),
            )
        },
        scope = viewModelScope,
    )
    val state: StateFlow<SupportUiState> get() = controller.state

    fun onTab(tab: SupportTab) = controller.onTab(tab)
    fun onType(type: TicketType) = controller.onType(type)
    fun onToggleProjectMenu() = controller.onToggleProjectMenu()
    fun onDismissProjectMenu() = controller.onDismissProjectMenu()
    fun onPickProject(id: String) = controller.onPickProject(id)
    fun onTitle(text: String) = controller.onTitle(text)
    fun onDescription(text: String) = controller.onDescription(text)
    fun onAttachments(picked: List<TicketAttachment>) = controller.onAttachments(picked)
    fun onRemoveAttachment(uri: String) = controller.onRemoveAttachment(uri)
    fun onSubmit() = controller.onSubmit()
    fun onSentHandled() = controller.onSentHandled()
    fun retryLoad() = controller.load()
    fun retryTickets() = controller.loadTickets()
}

/**
 * Màn 8 (đặc tả §3.8, Thay đổi 0.6 §6). Tách khỏi ViewModel để test.
 * - Mở từ 10a ([alertId] khác null): loại Kỹ thuật, dự án của cảnh báo, tiêu đề và mô tả điền sẵn.
 * - Mở từ 6: chọn sẵn dự án đang mở. Mở từ 14a / 18 ([projectId] rỗng): chưa chọn dự án (BO_SUNG §21).
 * - Gửi thành công: chuyển sang thẻ "Yêu cầu của tôi" (phiếu mới trên cùng), làm trống form, báo [SupportUiState.sent].
 */
class SupportController(
    private val projectId: String,
    alertId: String?,
    private val projectRepository: ProjectRepository,
    private val alertRepository: AlertRepository,
    private val ticketRepository: SupportTicketRepository,
    private val prefillDescription: (AlertDetail) -> String,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    /** Gửi kèm phiếu đầu tiên; sau khi gửi xong form trở về trống nên không gắn cảnh báo nữa. */
    private var alertId: String? = alertId
    private var ticketsJob: Job? = null

    init {
        load()
        loadTickets()
    }

    fun load() {
        _state.update { it.copy(load = FormLoad.Loading) }
        scope.launch {
            try {
                val projects = projectRepository.getProjects()
                val alert = alertId?.let { alertRepository.detail(projectId, it).first() }
                val draft = if (alert != null) {
                    TicketDraft(
                        type = TicketType.Technical,
                        projectId = defaultProject(projects.map { it.id }),
                        title = alertTicketTitle(alert),
                        description = prefillDescription(alert),
                    )
                } else {
                    TicketDraft(projectId = defaultProject(projects.map { it.id }))
                }
                _state.update {
                    it.copy(
                        load = FormLoad.Ready,
                        projects = projects,
                        alert = alert?.let { a -> AlertBanner(a.title, a.startedAt) },
                        draft = draft,
                    )
                }
            } catch (e: ProjectLoadException) {
                _state.update { it.copy(load = FormLoad.Error(e.kind == ProjectLoadException.Kind.NoConnection)) }
            } catch (e: AlertLoadException) {
                _state.update { it.copy(load = FormLoad.Error(e.kind == AlertLoadException.Kind.NoConnection)) }
            }
        }
    }

    fun loadTickets() {
        ticketsJob?.cancel()
        _state.update { it.copy(tickets = TicketsState.Loading) }
        ticketsJob = scope.launch {
            ticketRepository.tickets()
                .catch { e ->
                    if (e is SupportTicketException) _state.update { it.copy(tickets = TicketsState.Error(e.noConnection)) }
                    else throw e
                }
                .collect { list -> _state.update { it.copy(tickets = TicketsState.Content(list)) } }
        }
    }

    private fun defaultProject(ids: List<String>): String? = projectId.takeIf { it in ids }

    fun onTab(tab: SupportTab) = _state.update { it.copy(tab = tab, projectMenuOpen = false) }

    fun onType(type: TicketType) = edit { it.copy(type = type) }

    fun onToggleProjectMenu() = _state.update { it.copy(projectMenuOpen = !it.projectMenuOpen) }

    fun onDismissProjectMenu() = _state.update { it.copy(projectMenuOpen = false) }

    /** Chọn xong danh sách tự đóng (đặc tả §3.8). */
    fun onPickProject(id: String) {
        edit { it.copy(projectId = id) }
        onDismissProjectMenu()
    }

    fun onTitle(text: String) = edit { it.copy(title = text) }

    fun onDescription(text: String) = edit { it.copy(description = text) }

    fun onAttachments(picked: List<TicketAttachment>) = edit { it.withAttachments(picked) }

    fun onRemoveAttachment(uri: String) = edit { it.withoutAttachment(uri) }

    private fun edit(change: (TicketDraft) -> TicketDraft) = _state.update { it.copy(draft = change(it.draft)) }

    fun onSubmit() {
        val s = _state.value
        if (s.submitting || s.load != FormLoad.Ready) return
        if (s.draft.validate(s.hasProjects).isNotEmpty()) {
            _state.update { it.copy(showErrors = true, projectMenuOpen = false) }
            return
        }
        _state.update { it.copy(submitting = true, submitFailed = null, projectMenuOpen = false) }
        val names = s.projects.associate { it.id to it.name }
        val ticket = s.draft.toNewTicket(s.hasProjects, names::get, alertId)
        scope.launch {
            try {
                val created = ticketRepository.submit(ticket)
                alertId = null
                _state.update {
                    it.copy(
                        submitting = false,
                        tab = SupportTab.Mine,
                        alert = null,
                        draft = TicketDraft(projectId = defaultProject(it.projects.map { p -> p.id })),
                        showErrors = false,
                        sent = created,
                    )
                }
            } catch (e: SupportTicketException) {
                _state.update { it.copy(submitting = false, submitFailed = SubmitFailure(e.noConnection)) }
            }
        }
    }

    fun onSentHandled() = _state.update { it.copy(sent = null) }
}
