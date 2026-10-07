package vn.ggpower.inverter.ui.config

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.data.config.ConfigWriter
import vn.ggpower.inverter.data.config.WriteResult
import vn.ggpower.inverter.di.AppModule
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.OperatingMode
import vn.ggpower.inverter.domain.config.ProjectConfig
import vn.ggpower.inverter.domain.config.WriteErrorReaction
import vn.ggpower.inverter.domain.config.WriteOutcome
import vn.ggpower.inverter.domain.config.after
import vn.ggpower.inverter.domain.config.configControls
import vn.ggpower.inverter.domain.config.reaction
import vn.ggpower.inverter.navigation.Config
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ConfigViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ConfigRepository,
    @Named(AppModule.CONFIG_READ_ONLY) configReadOnly: Boolean,
) : ViewModel() {
    private val controller = ConfigController(
        savedStateHandle.toRoute<Config>().projectId, repository, configReadOnly, viewModelScope,
    )
    val state: StateFlow<ConfigUiState> get() = controller.state

    init { controller.load() }

    fun retry() = controller.load()
    fun refresh() = controller.refreshIfIdle()
    fun onSelectMode(mode: OperatingMode) = controller.onSelectMode(mode)
    fun onBackupSocChange(percent: Int) = controller.onBackupSocChange(percent)
    fun onBackupSocChangeFinished() = controller.onBackupSocChangeFinished()
    fun onConfirm() = controller.onConfirm()
    fun onCancelConfirm() = controller.onCancelConfirm()
    fun onNoticeAction() = controller.onNoticeAction()
    fun onNoticeDismissed(id: Long) = controller.onNoticeDismissed(id)
    fun onUnknownDismissed() = controller.onUnknownDismissed()
}

/**
 * Đọc cấu hình và luồng ghi của màn 16 (AGENTS.md §7; ADR-001; ADR-002 mục 15; BO_SUNG_v3.md §1–3):
 * chọn chế độ / thả thanh SOC → hộp xác nhận "cũ → mới" → gửi lệnh kèm `based_on_version` và `Idempotency-Key` →
 * hỏi lại theo `poll_after_ms` tới trạng thái cuối. Giá trị trên màn chỉ đổi khi lệnh `applied` (đọc lại cấu hình).
 * Tách khỏi ViewModel để test không cần SavedStateHandle.
 */
class ConfigController(
    private val projectId: String,
    private val repository: ConfigRepository,
    private val configReadOnly: Boolean,
    private val scope: CoroutineScope,
    newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private val writer = ConfigWriter(repository, newKey)
    private val _state = MutableStateFlow<ConfigUiState>(ConfigUiState.Loading)
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    /** Lỗi khoá ghi / chặn nhóm backend vừa trả; giữ tới khi tải lại cả màn. */
    private val reactions = mutableListOf<WriteErrorReaction>()
    private var lastRequest: ConfigRequest? = null
    private var noticeId = 0L

    private val content get() = _state.value as? ConfigUiState.Content

    fun load() {
        reactions.clear()
        _state.value = ConfigUiState.Loading
        scope.launch {
            _state.value = try {
                content(repository.getConfig(projectId))
            } catch (e: ConfigLoadException) {
                ConfigUiState.Error(noConnection = e.kind == ConfigLoadException.Kind.NoConnection)
            }
        }
    }

    fun onSelectMode(mode: OperatingMode) {
        val c = content ?: return
        val card = c.controls.modes.firstOrNull { it.mode == mode } ?: return
        if (card.state != ControlState.Enabled || mode == c.config.mode || c.writing != null) return
        _state.value = c.copy(confirm = ConfirmState(ConfigRequest.Mode(mode)))
    }

    fun onBackupSocChange(percent: Int) {
        val c = content ?: return
        if (c.controls.backupSoc != ControlState.Enabled || c.confirm != null) return
        _state.value = c.copy(backupSocDraft = percent)
    }

    fun onBackupSocChangeFinished() {
        val c = content ?: return
        if (c.controls.backupSoc != ControlState.Enabled || c.confirm != null) return
        if (c.backupSocDraft != c.config.backupSocPercent) {
            _state.value = c.copy(confirm = ConfirmState(ConfigRequest.BackupSoc(c.backupSocDraft)))
        }
    }

    fun onCancelConfirm() {
        val c = content ?: return
        _state.value = c.copy(confirm = null, backupSocDraft = c.config.backupSocPercent)
    }

    fun onConfirm() {
        val c = content ?: return
        val request = c.confirm?.request ?: return
        submit(c.copy(confirm = null), request)
    }

    /** Nút "Thử lại" của snackbar thất bại: gửi lại cùng thay đổi với khoá mới và phiên bản cấu hình hiện tại. */
    fun onNoticeAction() {
        val c = content ?: return
        val request = lastRequest ?: return
        if (c.notice?.kind != NoticeKind.Failed || c.writing != null) return
        submit(c.copy(notice = null), request)
    }

    fun onNoticeDismissed(id: Long) = _state.update { s ->
        if (s is ConfigUiState.Content && s.notice?.id == id) s.copy(notice = null) else s
    }

    fun onUnknownDismissed() = _state.update { s ->
        if (s is ConfigUiState.Content) s.copy(showUnknownDialog = false) else s
    }

    /** Đọc lại khi màn hiện lại; bỏ qua khi đang tải, đang ghi hoặc đang mở hộp xác nhận. */
    fun refreshIfIdle() {
        val c = content ?: return
        if (c.writing != null || c.confirm != null) return
        scope.launch { refresh() }
    }

    // ---------- Gửi lệnh và hỏi trạng thái ----------

    private fun submit(c: ConfigUiState.Content, request: ConfigRequest) {
        lastRequest = request
        _state.value = withControls(c.copy(writing = request.group))
        scope.launch {
            when (val result = writer.write(projectId, request, c.config.version)) {
                is WriteResult.Finished -> finish(result.outcome)
                is WriteResult.Rejected -> onRejected(request, result)
                is WriteResult.Failed -> done(notice = if (result.noConnection) NoticeKind.NoInternet else NoticeKind.Generic)
            }
        }
    }

    private suspend fun finish(outcome: WriteOutcome) {
        when (outcome) {
            WriteOutcome.Applied, WriteOutcome.AppliedWithoutDischarge, WriteOutcome.SavedNotWritten -> { refresh(); done(outcome.noticeKind()) }
            WriteOutcome.Unknown -> {
                refresh()
                _state.update { s -> if (s is ConfigUiState.Content) s.copy(showUnknownDialog = true) else s }
                done(notice = null)
            }
            else -> done(outcome.noticeKind())
        }
    }

    private suspend fun onRejected(request: ConfigRequest, rejected: WriteResult.Rejected) {
        when (val r = rejected.code.reaction()) {
            is WriteErrorReaction.Lock, is WriteErrorReaction.Block -> {
                reactions += r
                done(notice = null)
            }
            WriteErrorReaction.ReloadAndReconfirm -> {
                refresh()
                done(notice = null)
                val c = content ?: return
                if (!request.matches(c.config)) {
                    _state.value = c.copy(
                        confirm = ConfirmState(request, stale = true),
                        backupSocDraft = (request as? ConfigRequest.BackupSoc)?.percent ?: c.backupSocDraft,
                    )
                }
            }
            is WriteErrorReaction.Notify -> done(notice = r.message.noticeKind(), retryAfterSeconds = rejected.retryAfterSeconds)
        }
    }

    /** Kết thúc lệnh: mở khoá control, đưa thanh SOC về giá trị đã lưu, hiện snackbar nếu có. */
    private fun done(notice: NoticeKind?, retryAfterSeconds: Int? = null) = _state.update { s ->
        if (s !is ConfigUiState.Content) return@update s
        withControls(
            s.copy(
                writing = null,
                backupSocDraft = s.config.backupSocPercent,
                notice = notice?.let { ConfigNotice(++noticeId, it, retryAfterSeconds) } ?: s.notice,
            ),
        )
    }

    /** Đọc lại cấu hình mà không về trạng thái đang tải; lỗi thì giữ cấu hình cũ. */
    private suspend fun refresh() {
        val config = try { repository.getConfig(projectId) } catch (_: ConfigLoadException) { return }
        _state.update { s -> if (s is ConfigUiState.Content) withControls(s.copy(config = config)) else s }
    }

    private fun content(config: ProjectConfig) = withControls(
        ConfigUiState.Content(config, configControls(config.writeAccess, config.mode, configReadOnly)),
    )

    /** Tính lại control: quyền sau các lỗi khoá ghi, và khoá hết khi đang có lệnh chạy. */
    private fun withControls(c: ConfigUiState.Content): ConfigUiState.Content {
        val access = reactions.fold(c.config.writeAccess) { a, r -> a.after(r) }
        return c.copy(controls = configControls(access, c.config.mode, configReadOnly || c.writing != null))
    }

    private fun ConfigRequest.matches(config: ProjectConfig): Boolean = when (this) {
        is ConfigRequest.Mode -> mode == config.mode
        is ConfigRequest.BackupSoc -> percent == config.backupSocPercent
        is ConfigRequest.Tou -> strategies == config.strategies
    }

}
