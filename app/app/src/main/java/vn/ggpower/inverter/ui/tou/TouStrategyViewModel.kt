package vn.ggpower.inverter.ui.tou

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.config.ConfigLoadException
import vn.ggpower.inverter.data.config.ConfigRepository
import vn.ggpower.inverter.data.config.ConfigWriter
import vn.ggpower.inverter.data.config.WriteResult
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.WriteErrorReaction
import vn.ggpower.inverter.domain.config.WriteOutcome
import vn.ggpower.inverter.domain.config.reaction
import vn.ggpower.inverter.ui.config.ConfigNotice
import vn.ggpower.inverter.ui.config.NoticeKind
import vn.ggpower.inverter.ui.config.noticeKind
import vn.ggpower.inverter.di.AppModule
import vn.ggpower.inverter.domain.config.ControlState
import vn.ggpower.inverter.domain.config.configControls
import vn.ggpower.inverter.domain.tou.MAX_STRATEGY_NAME_LENGTH
import vn.ggpower.inverter.domain.tou.SlotInput
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.SlotValidation
import vn.ggpower.inverter.domain.tou.defaultAddKind
import vn.ggpower.inverter.domain.tou.normalizePowerInput
import vn.ggpower.inverter.domain.tou.normalizeTimeInput
import vn.ggpower.inverter.domain.tou.suggestSlot
import vn.ggpower.inverter.domain.tou.toInput
import vn.ggpower.inverter.domain.tou.validateSlot
import vn.ggpower.inverter.domain.tou.withSlotAdded
import vn.ggpower.inverter.domain.tou.withSlotRemoved
import vn.ggpower.inverter.domain.tou.withSlotReplaced
import vn.ggpower.inverter.domain.tou.strategyLetter
import vn.ggpower.inverter.domain.tou.toggleDay
import vn.ggpower.inverter.domain.tou.validateStrategyName
import vn.ggpower.inverter.domain.tou.withNewStrategy
import vn.ggpower.inverter.navigation.NEW_STRATEGY_ID
import vn.ggpower.inverter.navigation.TouStrategy
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TouStrategyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: ConfigRepository,
    @Named(AppModule.CONFIG_READ_ONLY) configReadOnly: Boolean,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TouStrategy>()
    private val controller = TouStrategyController(
        projectId = route.projectId,
        strategyId = route.strategyId,
        repository = repository,
        configReadOnly = configReadOnly,
        newName = { letter -> context.getString(R.string.tou_new_strategy_name, letter.toString()) },
        scope = viewModelScope,
    )
    val state: StateFlow<TouStrategyUiState> get() = controller.state

    init { retry() }

    fun retry() { viewModelScope.launch { controller.load() } }
    fun onToggleDay(day: DayOfWeek) = controller.onToggleDay(day)
    fun onRenameOpen() = controller.onRenameOpen()
    fun onRenameChange(text: String) = controller.onRenameChange(text)
    fun onRenameSave() = controller.onRenameSave()
    fun onRenameCancel() = controller.onRenameCancel()
    fun onBackRequest(): Boolean = controller.onBackRequest()
    fun onDiscardCancel() = controller.onDiscardCancel()
    fun onAddSlot() = controller.onAddSlot()
    fun onOpenSlot(index: Int) = controller.onOpenSlot(index)
    fun onSlotKindChange(kind: SlotKind) = controller.onSlotKindChange(kind)
    fun onSlotFieldChange(field: SlotField, text: String) = controller.onSlotFieldChange(field, text)
    fun onSlotSave() = controller.onSlotSave()
    fun onSlotDelete() = controller.onSlotDelete()
    fun onSlotCancel() = controller.onSlotCancel()
    fun onSave() = controller.onSave()
    fun onDeleteStrategy(): Boolean = controller.onDeleteStrategy()
    fun onConfirm() = controller.onConfirm()
    fun onCancelConfirm() = controller.onCancelConfirm()
    fun onNoticeAction() = controller.onNoticeAction()
    fun onNoticeDismissed(id: Long) = controller.onNoticeDismissed(id)
    fun onUnknownDismissed() = controller.onUnknownDismissed()
}

/**
 * Bản nháp của màn 16b (ADR-002 mục 2–5, 9). [strategyId] = [NEW_STRATEGY_ID]: thêm chiến lược trống vào cuối bản nháp
 * với tên [newName] ("Chiến lược C"…); rời màn khi chưa lưu thì chiến lược đó biến mất. Tách khỏi ViewModel để test.
 */
class TouStrategyController(
    private val projectId: String,
    private val strategyId: String,
    private val repository: ConfigRepository,
    private val configReadOnly: Boolean,
    private val newName: (Char) -> String,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    newKey: () -> String = { UUID.randomUUID().toString() },
) {
    private val writer = ConfigWriter(repository, newKey)
    private var lastConfirm: TouConfirm? = null
    private var noticeId = 0L

    private val _state = MutableStateFlow<TouStrategyUiState>(TouStrategyUiState.Loading)
    val state: StateFlow<TouStrategyUiState> = _state.asStateFlow()

    private val content get() = _state.value as? TouStrategyUiState.Content

    suspend fun load() {
        _state.value = TouStrategyUiState.Loading
        _state.value = try {
            val config = repository.getConfig(projectId)
            val saved = config.strategies
            val (draft, id) = if (strategyId == NEW_STRATEGY_ID) {
                val id = newId()
                saved.withNewStrategy(id, newName(strategyLetter(saved.size))) to id
            } else {
                saved to strategyId
            }
            if (draft.none { it.id == id }) {
                TouStrategyUiState.Error(noConnection = false)
            } else {
                val controls = configControls(config.writeAccess, config.mode, configReadOnly)
                TouStrategyUiState.Content(
                    version = config.version, saved = saved, draft = draft, strategyId = id,
                    editable = controls.touSchedule == ControlState.Enabled, mode = config.mode,
                )
            }
        } catch (e: ConfigLoadException) {
            TouStrategyUiState.Error(noConnection = e.kind == ConfigLoadException.Kind.NoConnection)
        }
    }

    fun onToggleDay(day: DayOfWeek) = edit { it.copy(draft = it.draft.toggleDay(it.strategyId, day)) }

    fun onRenameOpen() = edit { it.copy(rename = RenameState(it.strategy.name, null)) }

    /** Ô nhập giới hạn 40 ký tự; báo lỗi ngay khi trống. */
    fun onRenameChange(text: String) = edit { c ->
        val t = text.take(MAX_STRATEGY_NAME_LENGTH)
        c.copy(rename = RenameState(t, validateStrategyName(t)))
    }

    fun onRenameSave() = edit { c ->
        val r = c.rename ?: return@edit c
        val error = validateStrategyName(r.text)
        if (error != null) return@edit c.copy(rename = r.copy(error = error))
        val name = r.text.trim()
        c.copy(rename = null, draft = c.draft.map { if (it.id == c.strategyId) it.copy(name = name) else it })
    }

    fun onRenameCancel() = _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(rename = null) else s }

    /** Nút ← hoặc cử chỉ back: còn thay đổi chưa lưu thì hỏi (BO_SUNG_v3.md §4). Trả true nếu được rời màn ngay. */
    fun onBackRequest(): Boolean {
        val c = content ?: return true
        // Đang ghi: chờ kết quả rồi mới cho rời màn.
        if (c.writing) return false
        if (!c.hasChanges) return true
        _state.value = c.copy(showDiscard = true)
        return false
    }

    fun onDiscardCancel() = _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(showDiscard = false) else s }

    // ---------- Popup khung (Thay đổi 0.6 §5; ADR-002 mục 1, 6) ----------

    /** "Thêm khung sạc hoặc xả": loại mặc định, giờ gợi ý không trùng (ADR-002 mục 6). */
    fun onAddSlot() = edit { c ->
        val kind = c.strategy.defaultAddKind()
        c.copy(slotSheet = sheet(c, null, c.strategy.suggestSlot(kind).toInput()))
    }

    fun onOpenSlot(index: Int) = edit { c ->
        val slot = c.strategy.slots.getOrNull(index) ?: return@edit c
        c.copy(slotSheet = sheet(c, index, slot.toInput()))
    }

    /** Chỉ khi thêm mới: đổi loại thì điền lại giá trị gợi ý của loại đó (như prototype). */
    fun onSlotKindChange(kind: SlotKind) = edit { c ->
        val s = c.slotSheet ?: return@edit c
        if (s.editingIndex != null || s.input.kind == kind) return@edit c
        c.copy(slotSheet = sheet(c, null, c.strategy.suggestSlot(kind).toInput()))
    }

    fun onSlotFieldChange(field: SlotField, text: String) = edit { c ->
        val s = c.slotSheet ?: return@edit c
        val input = when (field) {
            SlotField.Start -> s.input.copy(start = normalizeTimeInput(text))
            SlotField.End -> s.input.copy(end = normalizeTimeInput(text))
            SlotField.Soc -> s.input.copy(stopSoc = text.filter { it.isDigit() }.take(3))
            SlotField.Power -> s.input.copy(maxPower = normalizePowerInput(text))
        }
        c.copy(slotSheet = sheet(c, s.editingIndex, input))
    }

    /** "Lưu" trong popup chỉ cập nhật bản nháp (ADR-002 mục 2); công suất được định dạng lại khi hiển thị. */
    fun onSlotSave() = edit { c ->
        val s = c.slotSheet ?: return@edit c
        val valid = validateSlot(s.input, c.strategy, s.editingIndex) as? SlotValidation.Valid ?: return@edit c
        val updated = if (s.editingIndex == null) c.strategy.withSlotAdded(valid.slot) else c.strategy.withSlotReplaced(s.editingIndex, valid.slot)
        c.copy(slotSheet = null, draft = c.draft.replace(updated))
    }

    /** "Xoá khung này": khung biến mất, các khung cùng loại dồn lên và đánh số lại (Thay đổi 0.6 §4). */
    fun onSlotDelete() = edit { c ->
        val index = c.slotSheet?.editingIndex ?: return@edit c
        c.copy(slotSheet = null, draft = c.draft.replace(c.strategy.withSlotRemoved(index)))
    }

    fun onSlotCancel() = _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(slotSheet = null) else s }

    private fun sheet(c: TouStrategyUiState.Content, editingIndex: Int?, input: SlotInput): SlotSheetState {
        val error = (validateSlot(input, c.strategy, editingIndex) as? SlotValidation.Invalid)?.error
        return SlotSheetState(editingIndex, input, error)
    }

    private fun List<vn.ggpower.inverter.domain.tou.TouStrategy>.replace(s: vn.ggpower.inverter.domain.tou.TouStrategy) =
        map { if (it.id == s.id) s else it }

    // ---------- Lưu lịch (ADR-002 mục 2, 8, 14, 15; BO_SUNG_v3.md §1–3) ----------

    /** "Lưu thay đổi" → hộp xác nhận liệt kê mọi thay đổi "cũ → mới". */
    fun onSave() = edit { c -> if (c.saveEnabled) c.copy(confirm = TouConfirm(c.draft)) else c }

    /**
     * "Xoá chiến lược này": chiến lược mới chưa lưu thì chỉ bỏ bản nháp (như rời màn — trả true nếu rời được ngay);
     * chiến lược đã lưu thì mở hộp xác nhận với danh sách không còn chiến lược này (ADR-002 mục 8).
     */
    fun onDeleteStrategy(): Boolean {
        val c = content ?: return false
        if (!c.canDelete) return false
        if (c.saved.none { it.id == c.strategyId }) return onBackRequest()
        _state.value = c.copy(confirm = TouConfirm(c.draft.filter { it.id != c.strategyId }, deleting = true))
        return false
    }

    fun onCancelConfirm() = _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(confirm = null) else s }

    fun onConfirm() {
        val c = content ?: return
        val confirm = c.confirm ?: return
        submit(c.copy(confirm = null), confirm)
    }

    /** "Thử lại" của snackbar thất bại: gửi lại cùng danh sách với khoá mới. */
    fun onNoticeAction() {
        val c = content ?: return
        val last = lastConfirm ?: return
        if (c.notice?.kind != NoticeKind.Failed || c.writing) return
        submit(c.copy(notice = null), last)
    }

    fun onNoticeDismissed(id: Long) = _state.update { s ->
        if (s is TouStrategyUiState.Content && s.notice?.id == id) s.copy(notice = null) else s
    }

    fun onUnknownDismissed() = _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(showUnknownDialog = false) else s }

    private fun submit(c: TouStrategyUiState.Content, confirm: TouConfirm) {
        lastConfirm = confirm
        _state.value = c.copy(writing = true)
        scope.launch {
            when (val result = writer.write(projectId, ConfigRequest.Tou(confirm.draft), c.version)) {
                is WriteResult.Finished -> finish(result.outcome, confirm)
                is WriteResult.Rejected -> onRejected(result, confirm)
                is WriteResult.Failed -> done(if (result.noConnection) NoticeKind.NoInternet else NoticeKind.Generic)
            }
        }
    }

    private suspend fun finish(outcome: WriteOutcome, confirm: TouConfirm) {
        when (outcome) {
            WriteOutcome.Applied, WriteOutcome.AppliedWithoutDischarge, WriteOutcome.SavedNotWritten -> {
                reload(resetDraft = true)
                if (confirm.deleting) _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(finished = true) else s }
                done(outcome.noticeKind())
            }
            WriteOutcome.Unknown -> {
                reload(resetDraft = true)
                _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(showUnknownDialog = true) else s }
                done(null)
            }
            else -> done(outcome.noticeKind())
        }
    }

    private suspend fun onRejected(rejected: WriteResult.Rejected, confirm: TouConfirm) {
        when (val r = rejected.code.reaction()) {
            is WriteErrorReaction.Lock -> {
                _state.update { s -> if (s is TouStrategyUiState.Content) s.copy(banner = r.state, editable = false) else s }
                done(null)
            }
            // Thiếu BMS chỉ chặn SOC dự phòng, không xảy ra với lịch TOU.
            is WriteErrorReaction.Block -> done(NoticeKind.Generic)
            WriteErrorReaction.ReloadAndReconfirm -> {
                // Giữ bản nháp của người dùng, so lại với cấu hình mới đọc rồi hỏi xác nhận lại.
                reload(resetDraft = false)
                done(null)
                _state.update { s ->
                    if (s is TouStrategyUiState.Content && s.changes.isNotEmpty()) s.copy(confirm = confirm.copy(stale = true)) else s
                }
            }
            is WriteErrorReaction.Notify -> done(r.message.noticeKind(), rejected.retryAfterSeconds)
        }
    }

    private fun done(notice: NoticeKind?, retryAfterSeconds: Int? = null) = _state.update { s ->
        if (s !is TouStrategyUiState.Content) return@update s
        s.copy(writing = false, notice = notice?.let { ConfigNotice(++noticeId, it, retryAfterSeconds) } ?: s.notice)
    }

    /**
     * Đọc lại cấu hình sau khi ghi; lỗi thì giữ trạng thái cũ. [resetDraft]: bản nháp = bản vừa lưu (chiến lược đang xem
     * vẫn có cùng id); không thì giữ bản nháp và chỉ cập nhật bản đã lưu + version.
     */
    private suspend fun reload(resetDraft: Boolean) {
        val config = try { repository.getConfig(projectId) } catch (_: ConfigLoadException) { return }
        _state.update { s ->
            if (s !is TouStrategyUiState.Content) return@update s
            // Chiến lược đang xem không còn trong bản đã lưu (vừa xoá, hoặc chiến lược mới chưa lưu được): giữ bản nháp.
            val keepDraft = !resetDraft || config.strategies.none { it.id == s.strategyId }
            s.copy(version = config.version, saved = config.strategies, draft = if (keepDraft) s.draft else config.strategies, mode = config.mode)
        }
    }

    private fun edit(block: (TouStrategyUiState.Content) -> TouStrategyUiState.Content) {
        val c = content ?: return
        if (!c.editable || c.writing) return
        _state.value = block(c)
    }
}
