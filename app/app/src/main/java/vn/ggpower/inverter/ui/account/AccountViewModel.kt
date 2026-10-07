package vn.ggpower.inverter.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.account.DeleteAccountResult
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.account.SaveProfileResult
import javax.inject.Inject

/**
 * Màn 15 · Thông tin tài khoản (đặc tả §3.15, Thay đổi 0.6 §7, BO_SUNG §24). Sửa họ tên và ảnh đại diện rồi bấm Lưu;
 * rời màn khi còn thay đổi thì hỏi; xoá tài khoản cần nhập mật khẩu.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AccountUiState>(AccountUiState.Loading)
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    val events: Flow<AccountEvent> = _events.receiveAsFlow()

    private var nextId = 0L

    init {
        viewModelScope.launch { _state.value = AccountUiState.Content(profiles.getProfile()) }
    }

    private inline fun updateContent(block: (AccountUiState.Content) -> AccountUiState.Content) =
        _state.update { if (it is AccountUiState.Content) block(it) else it }

    private val content get() = _state.value as? AccountUiState.Content

    fun onNameChange(name: String) = updateContent { it.copy(draft = it.draft.copy(name = name), nameError = null) }

    fun onAvatarPicked(uri: String) = updateContent { it.copy(draft = it.draft.copy(avatarUri = uri)) }

    fun onSave() {
        val c = content ?: return
        if (!c.hasChanges || c.saving) return
        val error = c.draft.nameError()
        if (error != null) {
            updateContent { it.copy(nameError = error) }
            return
        }
        updateContent { it.copy(saving = true, snackbar = null) }
        viewModelScope.launch {
            val draft = c.draft
            val result = profiles.updateProfile(draft.name.trim(), draft.avatarUri)
            updateContent {
                when (result) {
                    SaveProfileResult.Success -> {
                        val saved = draft.applyTo(it.saved)
                        // Giữ chữ người dùng gõ thêm trong lúc lưu; chỉ chuẩn hoá tên khi ô chưa đổi.
                        val newDraft = if (it.draft == draft) draft.copy(name = saved.name) else it.draft
                        it.copy(saved = saved, draft = newDraft, saving = false, snackbar = AccountSnackbar.Saved(++nextId))
                    }
                    SaveProfileResult.NoConnection -> it.copy(saving = false, snackbar = AccountSnackbar.SaveFailed(++nextId, noConnection = true))
                    SaveProfileResult.ServerError -> it.copy(saving = false, snackbar = AccountSnackbar.SaveFailed(++nextId, noConnection = false))
                }
            }
        }
    }

    fun onSnackbarShown(id: Long) = updateContent { if (it.snackbar?.id == id) it.copy(snackbar = null) else it }

    /** Nút ← hoặc nút Back hệ thống. Còn thay đổi chưa lưu thì hỏi trước (đặc tả §3.15). */
    fun onBack() {
        val c = content
        if (c != null && (c.hasChanges || c.saving)) updateContent { it.copy(discardOpen = true) } else _events.trySend(AccountEvent.Leave)
    }

    fun onDiscardCancel() = updateContent { it.copy(discardOpen = false) }

    fun onDiscardConfirm() {
        updateContent { it.copy(discardOpen = false) }
        _events.trySend(AccountEvent.Leave)
    }

    fun onDeleteOpen() = updateContent { it.copy(delete = DeleteDialogState()) }

    fun onDeleteDismiss() = updateContent { if (it.delete?.submitting == true) it else it.copy(delete = null) }

    fun onDeletePasswordChange(password: String) =
        updateContent { c -> c.copy(delete = c.delete?.copy(password = password, error = null)) }

    fun onDeleteConfirm() {
        val dialog = content?.delete ?: return
        if (!dialog.canSubmit) return
        updateContent { it.copy(delete = dialog.copy(submitting = true, error = null)) }
        viewModelScope.launch {
            val error = when (profiles.requestDeletion(dialog.password)) {
                DeleteAccountResult.Requested -> null
                DeleteAccountResult.WrongPassword -> DeleteError.WrongPassword
                DeleteAccountResult.NoConnection -> DeleteError.NoConnection
                DeleteAccountResult.ServerError -> DeleteError.Server
            }
            if (error == null) {
                updateContent { it.copy(delete = null) }
                _events.trySend(AccountEvent.Deleted)
            } else {
                updateContent { c -> c.copy(delete = c.delete?.copy(submitting = false, error = error)) }
            }
        }
    }
}

sealed interface AccountEvent {
    /** Rời màn (không còn thay đổi hoặc đã chọn bỏ thay đổi). */
    data object Leave : AccountEvent

    /** Đã gửi yêu cầu xoá: đăng xuất, về Đăng nhập kèm thông báo. */
    data object Deleted : AccountEvent
}
